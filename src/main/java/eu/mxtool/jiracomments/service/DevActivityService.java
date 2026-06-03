package eu.mxtool.jiracomments.service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import eu.mxtool.jiracomments.client.JiraClient;
import eu.mxtool.jiracomments.dto.BranchDetail;
import eu.mxtool.jiracomments.dto.BuildDetail;
import eu.mxtool.jiracomments.dto.CommitDetail;
import eu.mxtool.jiracomments.dto.DevActivity;
import eu.mxtool.jiracomments.dto.DevelopmentSummary;
import eu.mxtool.jiracomments.dto.PullRequestDetail;

/**
 * Retrieves and aggregates GitLab development activity for a single Jira issue.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DevActivityService {

    private final JiraClient jiraClient;

    public DevActivity getActivity(String issueId) {
        fetchSummary(issueId);

        List<DevActivity.BranchInfo> branches = fetchBranches(issueId);
        List<DevActivity.PrInfo> pullRequests = fetchPullRequests(issueId);
        List<DevActivity.RepoCommits> commitsByRepo = fetchCommitsByRepo(issueId);
        List<DevActivity.BuildInfo> builds = fetchBuilds(issueId);

        return new DevActivity(branches, pullRequests, commitsByRepo, builds);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private DevelopmentSummary fetchSummary(String issueId) {
        try {
            return jiraClient.getDevelopmentSummary(issueId);
        } catch (Exception ex) {
            log.warn("Could not fetch development summary for issue {}: {}", issueId, ex.getMessage());
            return null;
        }
    }

    private List<DevActivity.BranchInfo> fetchBranches(String issueId) {
        try {
            BranchDetail detail = jiraClient.getBranches(issueId);
            if (detail == null || detail.detail() == null) {
                return Collections.emptyList();
            }
            return detail.detail().stream()
                .filter(r -> r.branches() != null)
                .flatMap(r -> r.branches().stream())
                .filter(b -> b.name() != null)
                .map(b -> {
                    String repoUrl = repoUrlFromBranchUrl(b.url());
                    String repoName = repoNameFromUrl(repoUrl);
                    return new DevActivity.BranchInfo(repoName, repoUrl, b.name());
                })
                .toList();
        } catch (Exception ex) {
            log.warn("Could not fetch branches for issue {}: {}", issueId, ex.getMessage());
            return Collections.emptyList();
        }
    }

    private List<DevActivity.PrInfo> fetchPullRequests(String issueId) {
        try {
            PullRequestDetail detail = jiraClient.getPullRequests(issueId);
            if (detail == null || detail.detail() == null) {
                return Collections.emptyList();
            }
            return detail.detail().stream()
                .filter(r -> r.pullRequests() != null)
                .flatMap(r -> r.pullRequests().stream())
                .map(pr -> {
                    String repoUrl = repoUrlFromMrUrl(pr.url());
                    String repoName = pr.repositoryName() != null
                        ? pr.repositoryName()
                        : repoNameFromUrl(repoUrl);
                    List<String> reviewers = pr.reviewers() != null
                        ? pr.reviewers().stream()
                        .map(PullRequestDetail.Reviewer::name)
                        .filter(Objects::nonNull)
                        .toList()
                        : Collections.emptyList();
                        String date = pr.lastUpdate() != null
                                ? pr.lastUpdate()
                                : (pr.updatedDate() != null ? pr.updatedDate() : pr.createdDate());
                    return new DevActivity.PrInfo(
                        pr.id() != null ? pr.id() : "?",
                        pr.name() != null ? pr.name() : "(untitled)",
                        pr.status() != null ? pr.status() : "UNKNOWN",
                        pr.url(),
                        repoName,
                        repoUrl,
                        reviewers,
                        date);
                })
                .toList();
        } catch (Exception ex) {
            log.warn("Could not fetch pull requests for issue {}: {}", issueId, ex.getMessage());
            try {
                log.warn("Pull-request raw JSON for issue {}: {}", issueId, jiraClient.getPullRequestsRaw(issueId));
            } catch (Exception ignore) {
            }
            return Collections.emptyList();
        }
    }

    private List<DevActivity.RepoCommits> fetchCommitsByRepo(String issueId) {
        try {
            CommitDetail detail = jiraClient.getCommits(issueId);
            if (detail == null || detail.detail() == null) {
                return Collections.emptyList();
            }
            return detail.detail().stream()
                .filter(d -> d.repositories() != null)
                .flatMap(d -> d.repositories().stream())
                .filter(r -> r.commits() != null && !r.commits().isEmpty())
                .map(r -> new DevActivity.RepoCommits(
                    r.name() != null ? r.name().trim() : "Unknown",
                    r.url(),
                    r.commits().stream()
                        .map(c -> new DevActivity.CommitInfo(
                            c.displayId() != null ? c.displayId()
                                : (c.id() != null ? c.id().substring(0, Math.min(8, c.id().length())) : "?"),
                            firstLine(c.message()),
                            c.url(),
                            c.merge(),
                            c.authorTimestamp(),
                            c.fileCount(),
                            c.author() != null ? c.author().name() : null))
                        .toList()))
                .toList();
        } catch (Exception ex) {
            log.warn("Could not fetch commits for issue {}: {}", issueId, ex.getMessage());
            try {
                log.debug("Commit raw JSON for issue {}: {}", issueId, jiraClient.getCommitsRaw(issueId));
            } catch (Exception ignore) {
            }
            return Collections.emptyList();
        }
    }

    private List<DevActivity.BuildInfo> fetchBuilds(String issueId) {
        try {
            BuildDetail detail = jiraClient.getBuilds(issueId);
            if (detail == null || detail.detail() == null) {
                return Collections.emptyList();
            }
            return detail.detail().stream()
                .filter(d -> d.jswddBuildsData() != null)
                .flatMap(d -> d.jswddBuildsData().stream())
                .filter(e -> e.builds() != null)
                .flatMap(e -> e.builds().stream())
                .map(b -> new DevActivity.BuildInfo(
                    b.displayName() != null ? b.displayName() : "?",
                    b.buildNumber(),
                    b.url(),
                    b.state() != null ? b.state() : "unknown",
                    b.lastUpdated(),
                    b.testInfo() != null ? b.testInfo().numberPassed() : 0,
                    b.testInfo() != null ? b.testInfo().totalNumber() : 0))
                .toList();
        } catch (Exception ex) {
            log.warn("Could not fetch builds for issue {}: {}", issueId, ex.getMessage());
            return Collections.emptyList();
        }
    }

    // ── URL helpers ───────────────────────────────────────────────────────────

    /**
     * Strips the branch-specific path segment from a GitLab branch URL to obtain the repository root URL.
     * <p>Example: {@code https://gitlab.com/group/repo/-/tree/main} → {@code https://gitlab.com/group/repo}
     */
    private String repoUrlFromBranchUrl(String branchUrl) {
        if (branchUrl == null || branchUrl.isBlank()) {
            return null;
        }
        int idx = branchUrl.indexOf("/-/tree/");
        if (idx < 0) {
            idx = branchUrl.indexOf("/-/");
        }
        return idx > 0 ? branchUrl.substring(0, idx) : branchUrl;
    }

    /**
     * Strips the merge-request path from a GitLab MR URL to obtain the repository root URL.
     * <p>Example: {@code https://gitlab.com/group/repo/-/merge_requests/42} → {@code https://gitlab.com/group/repo}
     */
    private String repoUrlFromMrUrl(String mrUrl) {
        if (mrUrl == null || mrUrl.isBlank()) {
            return null;
        }
        int idx = mrUrl.indexOf("/-/merge_requests");
        if (idx < 0) {
            idx = mrUrl.indexOf("/-/");
        }
        return idx > 0 ? mrUrl.substring(0, idx) : mrUrl;
    }

    /**
     * Derives a human-readable repository name from a repository root URL. Returns the last two path segments (e.g. "group/repo").
     */
    private String repoNameFromUrl(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return "Unknown";
        }
        String[] parts = repoUrl.replaceAll("/+$", "").split("/");
        if (parts.length >= 2) {
            return parts[parts.length - 2] + "/" + parts[parts.length - 1];
        }
        return repoUrl;
    }

    /**
     * Returns the first non-blank line of a (possibly multi-line) commit message.
     */
    private String firstLine(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        return message.lines()
            .filter(l -> !l.isBlank())
            .findFirst()
            .orElse("")
            .trim();
    }
}
