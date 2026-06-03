package eu.mxtool.jiracomments.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Response from {@code GET /rest/dev-status/latest/issue/detail?dataType=repository}.
 *
 * <p>Actual structure:
 * <pre>{@code
 * { "errors": [], "detail": [{ "repositories": [{ "name": "my-repo", "commits": [{id, displayId, message, url, merge}] }] }] }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CommitDetail(
        List<DetailItem> detail,
        List<Object> errors
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DetailItem(
            List<Repository> repositories
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Repository(
            String name,
            String url,
            List<Commit> commits
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Commit(
            String id,
            /** Short hash shown in GitLab UI. */
            String displayId,
            /** Full commit message (may be multi-line). */
            String message,
            String url,
            /** True for merge commits — useful for filtering. */
            boolean merge,
            /** ISO-8601 author timestamp (e.g. "2026-06-03T10:00:00.000+0000"). */
            String authorTimestamp,
            /** Number of files changed in this commit. */
            int fileCount,
            /** Author — the API returns this as a nested object. */
            Author author
    ) {}

    /** Commit author object as returned by the dev-status API. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Author(
            String name,
            String emailAddress,
            String avatarUrl
    ) {}
}
