package eu.mxtool.jiracomments.service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import eu.mxtool.jiracomments.client.JiraClient;
import eu.mxtool.jiracomments.config.JiraProperties;
import eu.mxtool.jiracomments.dto.DevActivity;
import eu.mxtool.jiracomments.dto.Issue;
import eu.mxtool.jiracomments.dto.SearchResponse;

/**
 * Orchestrates the full backfill process.
 *
 * <p>Processing pipeline for each issue:
 * <ol>
 *   <li>Check for an existing generated comment → skip if found</li>
 *   <li>Fetch development activity from Jira dev-status APIs</li>
 *   <li>Skip if there is no activity (no branches, commits, or PRs)</li>
 *   <li>Generate the comment body via {@link CommentGeneratorService}</li>
 *   <li>Post the comment via {@link JiraClient}</li>
 * </ol>
 *
 * <p>A configurable inter-request delay ({@code jira.request-delay-ms}) is
 * applied after each issue to stay well within Jira Cloud rate limits.
 * Individual issue failures are caught and logged — they do not abort the run.
 *
 * <p>At the end of every run a plain-text report file is written to the working
 * directory listing which issue keys were processed, commented, skipped, or failed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackfillService {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss");
    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JiraClient jiraClient;
    private final DevActivityService devActivityService;
    private final CommentGeneratorService commentGeneratorService;
    private final JiraProperties properties;

    // ── Per-run tracking ───────────────────────────────────────────────────────

    private final List<String> processedKeys = new ArrayList<>();
    private final List<String> commentedKeys = new ArrayList<>();
    private final List<String> skippedKeys   = new ArrayList<>();
    private final List<String> failedKeys    = new ArrayList<>();

    // ── Counters (derived from list sizes, kept for log convenience) ───────────

    private int processed;
    private int commented;
    private int skipped;
    private int failed;

    // ── Resume / progress state ────────────────────────────────────────────────

    /** Last ticket key touched (processed, skipped, or failed). */
    private volatile String lastProcessedKey = null;

    /** Last page number reached during a full backfill. */
    private volatile int lastPageNum = 0;

    /** Snapshot of run metadata needed by the shutdown hook. */
    private volatile LocalDateTime runStartTime = null;
    private volatile String        runMode      = null;

    /** Guards against writing the report twice (normal exit + shutdown hook). */
    private final AtomicBoolean reportWritten = new AtomicBoolean(false);

    // ── Public API ─────────────────────────────────────────────────────────────

    public void run(List<String> issueKeys) {
        resetCounters();
        runStartTime = LocalDateTime.now();

        boolean fullRun = issueKeys.stream().anyMatch(k -> "ALL".equalsIgnoreCase(k));
        runMode = fullRun ? "FULL BACKFILL (ALL)"
                         : "TARGETED (" + String.join(", ", issueKeys) + ")";

        int limit = properties.getMaxSuccessfulProcessed();
        if (limit > 0) {
            log.info("Limit: stop after {} successfully commented issue(s).", limit);
        }

        if (fullRun) {
            log.info("=== FULL BACKFILL — project: {} ===", properties.getProjectKey());
            runFullBackfill();
        } else {
            log.info("=== TARGETED RUN — processing {} issue(s): {} ===",
                    issueKeys.size(), issueKeys);
            for (String key : issueKeys) {
                if (isLimitReached()) break;
                try {
                    Issue issue = jiraClient.findIssue(key.trim());
                    processIssue(issue);
                } catch (Exception ex) {
                    log.error("[FAIL ] {} — could not fetch issue: {}", key, ex.getMessage());
                    log.error("Hint: verify your API token has permission to view this issue in Jira.");
                    failedKeys.add(key.trim());
                    failed++;
                }
            }
        }

        logSummary();
        writeReport();
    }

    // ── Shutdown hook — fires on Ctrl+C / SIGTERM ─────────────────────────────

    @PreDestroy
    public void onShutdown() {
        if (runStartTime != null) {
            log.info("Shutdown signal received — flushing report...");
            writeReport();
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private void runFullBackfill() {
        String jql      = "project = " + properties.getProjectKey() + " ORDER BY created ASC";
        int    startPage = properties.getStartPage();
        int    endPage   = properties.getEndPage();
        int    pageSize  = properties.getPageSize();

        // Jump directly to startPage by computing the issue offset for the first request.
        // Subsequent pages use the cursor token returned by Jira.
        String nextPageToken  = null;
        int    initialStartAt = startPage * pageSize;
        lastPageNum = startPage; // pages 1..startPage are skipped via offset, not fetched

        if (startPage > 0) {
            log.info("Jumping directly to page {} (startAt={})", startPage + 1, initialStartAt);
        }

        while (true) {
            if (isLimitReached()) break;

            SearchResponse page = jiraClient.searchIssues(jql, nextPageToken, initialStartAt, pageSize);
            initialStartAt = 0; // offset only used for the very first request
            lastPageNum++;

            nextPageToken = page.nextPageToken();


            log.info("--- Page {} ---", lastPageNum);

            for (Issue issue : page.issues()) {
                if (isLimitReached()) break;
                processIssue(issue);
                sleep(properties.getRequestDelayMs());
            }

            if (page.issues().isEmpty()) {
                log.warn("Received empty page on page {}; stopping pagination.", lastPageNum);
                break;
            }

            if (endPage > 0 && lastPageNum >= endPage) {
                log.info("Reached end-page limit ({}) — stopping.", endPage);
                break;
            }

            if (nextPageToken == null) {
                break; // last page
            }
        }
    }

    private void processIssue(Issue issue) {
        processed++;
        String key = issue.key();
        String id  = issue.id();
        processedKeys.add(key);
        lastProcessedKey = key;

        try {
            if (jiraClient.hasGeneratedComment(key)) {
                log.info("[SKIP ] {} — summary comment already exists", key);
                skippedKeys.add(key);
                skipped++;
                return;
            }

            DevActivity activity = devActivityService.getActivity(id);

            if (!activity.hasActivity()) {
                log.info("[SKIP ] {} — no development activity found", key);
                skippedKeys.add(key);
                skipped++;
                return;
            }

            Map<String, Object> comment = commentGeneratorService.generate(key, activity);
            jiraClient.addComment(key, comment);

            log.info("[OK   ] {} — comment added (branches={}, PRs={}, commits={}, builds={})",
                    key,
                    activity.branches().size(),
                    activity.pullRequests().size(),
                    activity.totalCommits(),
                    activity.builds().size());
            commentedKeys.add(key);
            commented++;

        } catch (Exception ex) {
            log.error("[FAIL ] {} — {}", key, ex.getMessage());
            failedKeys.add(key);
            failed++;
        }
    }

    private boolean isLimitReached() {
        int limit = properties.getMaxSuccessfulProcessed();
        if (limit > 0 && commented >= limit) {
            log.info("Limit of {} successful comment(s) reached — stopping.", limit);
            return true;
        }
        return false;
    }

    private void resetCounters() {
        processed = 0;
        commented = 0;
        skipped   = 0;
        failed    = 0;
        lastProcessedKey = null;
        lastPageNum      = 0;
        reportWritten.set(false);
        processedKeys.clear();
        commentedKeys.clear();
        skippedKeys.clear();
        failedKeys.clear();
    }

    private void logSummary() {
        log.info("==========================================================");
        log.info("Backfill complete");
        log.info("  Total processed : {}", processed);
        log.info("  Comments added  : {}", commented);
        log.info("  Skipped         : {}", skipped);
        log.info("  Failed          : {}", failed);
        if (lastProcessedKey != null) log.info("  Last ticket     : {}", lastProcessedKey);
        if (lastPageNum > 0)         log.info("  Last page       : {}", lastPageNum);
        log.info("==========================================================");
    }

    private void writeReport() {
        if (!reportWritten.compareAndSet(false, true)) return; // already written

        LocalDateTime startTime = runStartTime;
        if (startTime == null) return;

        String fileName = "jira-backfill-" + startTime.format(TIMESTAMP_FMT) + ".txt";
        Path reportPath = Path.of(fileName);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(reportPath))) {
            pw.println("Jira Backfill Report");
            pw.println("Generated  : " + LocalDateTime.now().format(DISPLAY_FMT));
            pw.println("Project    : " + properties.getProjectKey());
            pw.println("Mode       : " + runMode);
            pw.println("Start time : " + startTime.format(DISPLAY_FMT));
            pw.println("End time   : " + LocalDateTime.now().format(DISPLAY_FMT));
            pw.println();
            pw.println("── Run Arguments ─────────────────────────────────────");
            pw.println("--jira.project-key           : " + properties.getProjectKey());
            pw.println("--jira.issue-keys            : " + properties.getIssueKeys());
            pw.println("--jira.page-size             : " + properties.getPageSize());
            pw.println("--jira.start-page            : " + properties.getStartPage());
            pw.println("--jira.end-page              : " + (properties.getEndPage() > 0 ? properties.getEndPage() : "no limit"));
            pw.println("--jira.request-delay-ms      : " + properties.getRequestDelayMs());
            pw.println("--jira.max-retries           : " + properties.getMaxRetries());
            pw.println("--jira.retry-delay-ms        : " + properties.getRetryDelayMs());
            pw.println("--jira.max-successful-processed: " + (properties.getMaxSuccessfulProcessed() > 0 ? properties.getMaxSuccessfulProcessed() : "no limit"));
            pw.println();
            pw.println("── Progress ──────────────────────────────────────────");
            pw.println("Last page processed  : " + (lastPageNum > 0 ? lastPageNum : "N/A"));
            pw.println("Last ticket processed: " + (lastProcessedKey != null ? lastProcessedKey : "N/A"));
            pw.println();
            pw.println("── Summary ───────────────────────────────────────────");
            pw.println("Total processed : " + processed);
            pw.println("Comments added  : " + commented);
            pw.println("Skipped         : " + skipped);
            pw.println("Failed          : " + failed);
            pw.println();
            writeSection(pw, "COMMENTED (" + commented + ")", commentedKeys);
            writeSection(pw, "SKIPPED ("  + skipped   + ")", skippedKeys);
            writeSection(pw, "FAILED ("   + failed    + ")", failedKeys);
            writeSection(pw, "ALL PROCESSED (" + processed + ")", processedKeys);
        } catch (IOException ex) {
            log.error("Could not write report file {}: {}", reportPath, ex.getMessage());
            return;
        }
        log.info("Report written → {}", reportPath.toAbsolutePath());
    }

    private void writeSection(PrintWriter pw, String heading, List<String> keys) {
        pw.println(heading + ":");
        if (keys.isEmpty()) {
            pw.println("  (none)");
        } else {
            keys.forEach(k -> pw.println("  " + k));
        }
        pw.println();
    }

    private void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
