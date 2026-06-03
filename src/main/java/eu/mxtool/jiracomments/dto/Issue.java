package eu.mxtool.jiracomments.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A Jira issue as returned by the search endpoint (REST API v3)
 * or the single-issue endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Issue(
        String id,
        String key,
        IssueFields fields
) {}

