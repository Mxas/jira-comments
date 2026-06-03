package eu.mxtool.jiracomments.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A single Jira comment as returned by the REST API v2 endpoint.
 * The {@code body} field is plain Jira wiki markup when using v2.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CommentItem(String id, String body) {}

