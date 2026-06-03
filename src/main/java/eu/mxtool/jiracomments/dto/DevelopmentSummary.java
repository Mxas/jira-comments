package eu.mxtool.jiracomments.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response from {@code GET /rest/dev-status/latest/issue/summary}.
 *
 * <p>Actual structure:
 * <pre>{@code
 * {
 *   "summary": {
 *     "branch":      { "overall": { "count": 0 } },
 *     "pullrequest": { "overall": { "count": 6, "state": "MERGED" } },
 *     "repository":  { "overall": { "count": 34 } },
 *     "build":       { "overall": { "count": 13, "successfulBuildCount": 11, "failedBuildCount": 0 } }
 *   },
 *   "errors": []
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DevelopmentSummary(
        Summary summary,
        List<Object> errors
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(
            SummaryEntry branch,
            @JsonProperty("pullrequest") SummaryEntry pullRequest,
            SummaryEntry repository,
            SummaryEntry build
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SummaryEntry(
            Overall overall
    ) {
        public int count() {
            return overall != null ? overall.count() : 0;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Overall(
            int count,
            /** Overall state string, e.g. "OPEN", "MERGED". May be null for builds. */
            String state,
            /** Null for non-build summary entries. */
            Integer failedBuildCount,
            Integer successfulBuildCount,
            Integer unknownBuildCount
    ) {
        public int safeFailedCount()     { return failedBuildCount     != null ? failedBuildCount     : 0; }
        public int safeSuccessfulCount() { return successfulBuildCount != null ? successfulBuildCount : 0; }
        public int safeUnknownCount()    { return unknownBuildCount    != null ? unknownBuildCount    : 0; }
    }
}
