package eu.mxtool.jiracomments.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Response from {@code GET /rest/dev-status/latest/issue/detail?dataType=pullrequest}.
 *
 * <p>Actual structure:
 * <pre>{@code
 * { "errors": [], "detail": [{ "branches": [], "pullRequests": [{id, name, status, url, source, destination}], "repositories": [] }] }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PullRequestDetail(
        List<DetailItem> detail,
        List<Object> errors
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DetailItem(
            List<PullRequest> pullRequests
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullRequest(
            /** GitLab MR path, e.g. "my-group/my-repo!42". */
            String id,
            /** MR title. */
            String name,
            /** OPEN | MERGED | DECLINED */
            String status,
            String url,
            BranchRef source,
            BranchRef destination,
            String repositoryName,
            List<Reviewer> reviewers,
            /** Timestamp of last update — epoch-ms number or ISO-8601 string depending on API version. */
            String lastUpdate,
            /** ISO-8601 fallback dates. */
            String createdDate,
            String updatedDate
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BranchRef(String branch, String url) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Reviewer(String name, Boolean approved) {}
}
