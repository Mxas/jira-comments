package eu.mxtool.jiracomments.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Paginated list of comments returned by {@code GET /rest/api/2/issue/{issueKey}/comment}.
 * Using API v2 because v3 returns comments in Atlassian Document Format (ADF),
 * making plain-text duplicate detection impractical.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CommentPage(
        List<CommentItem> comments,
        int total,
        int maxResults,
        int startAt
) {}

