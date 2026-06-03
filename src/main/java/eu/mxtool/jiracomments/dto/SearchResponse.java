package eu.mxtool.jiracomments.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Paginated response from {@code GET /rest/api/3/search/jql}.
 * The new endpoint uses cursor-based pagination: {@code nextPageToken} is present
 * when there are more results, absent/null on the last page.
 * {@code total} is no longer returned by the new endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchResponse(
        List<Issue> issues,
        String nextPageToken
) {}
