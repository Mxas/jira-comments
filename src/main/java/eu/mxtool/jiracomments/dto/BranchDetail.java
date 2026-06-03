package eu.mxtool.jiracomments.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Response from {@code GET /rest/dev-status/latest/issue/detail?dataType=branch}.
 *
 * <p>Actual structure:
 * <pre>{@code
 * { "errors": [], "detail": [{ "branches": [...], "pullRequests": [...], "repositories": [] }] }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BranchDetail(
        List<DetailItem> detail,
        List<Object> errors
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DetailItem(
            List<Branch> branches
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Branch(
            String name,
            String url,
            LastCommit lastCommit
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LastCommit(
            String id,
            String displayId,
            String message
    ) {}
}
