package eu.mxtool.jiracomments.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Response from {@code GET /rest/dev-status/latest/issue/detail?applicationType=cloud-providers&dataType=build}.
 *
 * <p>Actual structure:
 * <pre>{@code
 * { "detail": [{ "jswddBuildsData": [{ "builds": [{
 *   "displayName": "PROJ-123/feature-branch",
 *   "buildNumber": 723,
 *   "url": "https://gitlab.com/.../pipelines/...",
 *   "state": "successful",
 *   "lastUpdated": "2026-06-02T11:17:46Z",
 *   "testInfo": { "totalNumber": 1, "numberPassed": 1, "numberFailed": 0, "numberSkipped": 0 }
 * }] }] }] }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BuildDetail(
        List<DetailItem> detail,
        List<Object> errors
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DetailItem(
            List<JswddBuildsEntry> jswddBuildsData
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JswddBuildsEntry(
            List<Build> builds
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Build(
            /** Branch name used as pipeline label, e.g. "PROJ-123/feature-branch". */
            String displayName,
            /** GitLab pipeline number, e.g. 723. */
            int buildNumber,
            /** Direct URL to the GitLab pipeline page. */
            String url,
            /** "successful" | "failed" | "cancelled" | "running" | "pending" */
            String state,
            /** ISO-8601 timestamp, e.g. "2026-06-02T11:17:46Z". */
            String lastUpdated,
            TestInfo testInfo
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TestInfo(
            int totalNumber,
            int numberPassed,
            int numberFailed,
            int numberSkipped
    ) {}
}

