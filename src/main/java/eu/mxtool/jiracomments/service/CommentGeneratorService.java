package eu.mxtool.jiracomments.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import eu.mxtool.jiracomments.dto.DevActivity;

import static eu.mxtool.jiracomments.service.AdfBuilder.*;

/**
 * Generates a Jira comment as an Atlassian Document Format (ADF) document.
 *
 * <p>The ADF is posted to Jira via the REST API v3, which supports the
 * {@code expand} node (collapsible panel) natively.
 */
@Service
public class CommentGeneratorService {

    public static final String DUPLICATE_MARKER =
            "Generated automatically from Jira Development panel.";

    /** Maximum number of commits shown per repository before truncation. */
    static final int MAX_COMMITS_SHOWN = 20;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    /** Returns the ADF document as a {@code Map} ready to be serialised and posted to Jira v3. */
    public Map<String, Object> generate(String issueKey, DevActivity activity) {
        List<Map<String, Object>> content = new ArrayList<>();

        content.add(heading(1, "Git Activity Summary"));

        appendBranches(content, activity);
        appendPullRequests(content, activity);
        appendCommits(content, activity);
        appendBuilds(content, activity);

        content.add(para(List.of(em(DUPLICATE_MARKER))));

        return doc(content);
    }

    // ── Section builders ──────────────────────────────────────────────────────

    private void appendBranches(List<Map<String, Object>> out, DevActivity activity) {
        if (activity.branches().isEmpty()) return;

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(tr(List.of(th("Repository"), th("Branch Name"))));
        for (DevActivity.BranchInfo b : activity.branches()) {
            rows.add(tr(List.of(
                    td(List.of(link(b.repoName(), b.repoUrl()))),
                    td(List.of(code(b.branchName())))
            )));
        }
        out.add(expand("Branches (" + activity.branches().size() + ")", List.of(table(rows))));
    }

    private void appendPullRequests(List<Map<String, Object>> out, DevActivity activity) {
        if (activity.pullRequests().isEmpty()) return;

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(tr(List.of(
                th("Repository"), th("ID"), th("Message"), th("Status"), th("Reviewers"), th("Date")
        )));
        for (DevActivity.PrInfo pr : activity.pullRequests()) {
            String mrNum = pr.id().contains("!")
                    ? "!" + pr.id().substring(pr.id().lastIndexOf('!') + 1)
                    : pr.id();
            String reviewers = pr.reviewers() == null || pr.reviewers().isEmpty()
                    ? "—"
                    : String.join(", ", pr.reviewers());
            rows.add(tr(List.of(
                    td(List.of(link(pr.repoName() != null ? pr.repoName() : "—", pr.repoUrl()))),
                    td(List.of(link(mrNum, pr.url()))),
                    td(List.of(code(pr.name()))),
                    td(List.of(text(pr.status()))),
                    td(List.of(text(reviewers))),
                    td(List.of(text(formatDate(pr.date()))))
            )));
        }
        out.add(expand("Merge Requests (" + activity.pullRequests().size() + ")", List.of(table(rows))));
    }

    private void appendCommits(List<Map<String, Object>> out, DevActivity activity) {
        int total = activity.totalCommits();
        if (total == 0) return;

        List<Map<String, Object>> expandContent = new ArrayList<>();
        int shown = 0;

        for (DevActivity.RepoCommits repo : activity.commitsByRepo()) {
            if (repo.commits().isEmpty() || shown >= MAX_COMMITS_SHOWN) continue;

            String repoName = repo.repoName() != null ? repo.repoName() : "Unknown";
            expandContent.add(para(List.of(boldLink(repoName, repo.repoUrl()))));

            List<Map<String, Object>> rows = new ArrayList<>();
            rows.add(tr(List.of(
                    th("Commit"), th("Message"), th("Author"), th("Changed Files"), th("Date")
            )));
            for (DevActivity.CommitInfo c : repo.commits()) {
                if (shown >= MAX_COMMITS_SHOWN) break;
                rows.add(tr(List.of(
                        td(List.of(codeLink(c.displayId(), c.url()))),
                        td(List.of(code(c.message()))),
                        td(List.of(text(c.author() != null && !c.author().isBlank() ? c.author() : "—"))),
                        td(List.of(text(c.fileCount() > 0 ? String.valueOf(c.fileCount()) : "—"))),
                        td(List.of(text(formatDate(c.date()))))
                )));
                shown++;
            }
            expandContent.add(table(rows));
        }

        if (total > shown) {
            expandContent.add(para(List.of(em("… and " + (total - shown) + " more commits"))));
        }
        out.add(expand("Commits (" + total + ")", expandContent));
    }

    private void appendBuilds(List<Map<String, Object>> out, DevActivity activity) {
        if (activity.builds().isEmpty()) return;

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(tr(List.of(
                th("Pipeline"), th("Build"), th("State"), th("Tests"), th("Updated")
        )));
        for (DevActivity.BuildInfo b : activity.builds()) {
            String stateIcon = switch (b.state().toLowerCase()) {
                case "successful" -> "✅";
                case "failed"     -> "❌";
                case "cancelled"  -> "⚠️";
                default           -> "❓";
            };
            String tests = b.testsTotal() > 0
                    ? b.testsPassed() + " / " + b.testsTotal() + " passed"
                    : "—";
            rows.add(tr(List.of(
                    td(List.of(code(b.pipelineName()))),
                    td(List.of(link("#" + b.buildNumber(), b.url()))),
                    td(List.of(text(stateIcon))),
                    td(List.of(text(tests))),
                    td(List.of(text(formatDate(b.lastUpdated()))))
            )));
        }
        out.add(expand("Pipelines (" + activity.builds().size() + ")", List.of(table(rows))));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String formatDate(String iso) {
        if (iso == null || iso.isBlank()) return "—";
        try {
            if (iso.matches("\\d+")) {
                return DATE_FMT.format(Instant.ofEpochMilli(Long.parseLong(iso))) + " UTC";
            }
            return DATE_FMT.format(Instant.parse(iso)) + " UTC";
        } catch (Exception e) {
            return iso;
        }
    }
}
