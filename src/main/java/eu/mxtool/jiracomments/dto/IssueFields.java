package eu.mxtool.jiracomments.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Subset of Jira issue fields we actually need.
 * {@code @JsonIgnoreProperties} silences the many other fields Jira returns.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IssueFields(String summary) {}

