package eu.mxtool.jiracomments.dto;

import java.util.List;

/**
 * Aggregated development activity for a single Jira issue,
 * assembled from all relevant dev-status detail endpoints.
 */
public record DevActivity(
        List<BranchInfo>   branches,
        List<PrInfo>       pullRequests,
        List<RepoCommits>  commitsByRepo,
        List<BuildInfo>    builds
) {

    /** Returns {@code true} if there is at least one branch, commit, or pull request. */
    public boolean hasActivity() {
        boolean hasCommits = commitsByRepo.stream().anyMatch(r -> !r.commits().isEmpty());
        return !branches.isEmpty() || !pullRequests.isEmpty() || hasCommits;
    }

    /** Total number of commits across all repositories. */
    public int totalCommits() {
        return commitsByRepo.stream().mapToInt(r -> r.commits().size()).sum();
    }

    // ── Nested value types ─────────────────────────────────────────────────────

    /** A single GitLab branch with its repository context. */
    public record BranchInfo(String repoName, String repoUrl, String branchName) {}

    /** Distilled pull-request / merge-request data needed for the comment. */
    public record PrInfo(
            String id,
            String name,
            String status,
            String url,
            String repoName,
            String repoUrl,
            List<String> reviewers,
            String date
    ) {}

    /** Distilled commit data needed for the comment. */
    public record CommitInfo(String displayId, String message, String url, boolean merge,
                             String date, int fileCount, String author) {}

    /** Commits from a single GitLab repository. */
    public record RepoCommits(String repoName, String repoUrl, List<CommitInfo> commits) {}

    /**
     * Single GitLab pipeline run associated with this Jira issue.
     *
     * @param pipelineName branch name used as pipeline label (e.g. "PROJ-123/feature-branch")
     * @param buildNumber  GitLab pipeline number
     * @param url          direct link to the GitLab pipeline page
     * @param state        "successful" | "failed" | "cancelled" | "running" | "pending"
     * @param lastUpdated  ISO-8601 timestamp from the API
     * @param testsPassed  number of tests that passed
     * @param testsTotal   total number of tests executed
     */
    public record BuildInfo(
            String pipelineName,
            int    buildNumber,
            String url,
            String state,
            String lastUpdated,
            int    testsPassed,
            int    testsTotal
    ) {}
}
