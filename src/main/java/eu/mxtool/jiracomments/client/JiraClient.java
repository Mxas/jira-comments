package eu.mxtool.jiracomments.client;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import eu.mxtool.jiracomments.config.JiraProperties;
import eu.mxtool.jiracomments.dto.BranchDetail;
import eu.mxtool.jiracomments.dto.BuildDetail;
import eu.mxtool.jiracomments.dto.CommitDetail;
import eu.mxtool.jiracomments.dto.DevelopmentSummary;
import eu.mxtool.jiracomments.dto.Issue;
import eu.mxtool.jiracomments.dto.PullRequestDetail;
import eu.mxtool.jiracomments.dto.SearchResponse;
import eu.mxtool.jiracomments.exception.JiraClientException;

/**
 * Low-level Jira REST API client.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Authentication (Basic Auth via API token)</li>
 *   <li>HTTP request execution via {@link RestTemplate}</li>
 *   <li>Retry with exponential back-off on HTTP 429 and 5xx responses</li>
 *   <li>Transparent pagination for comment duplicate detection</li>
 * </ul>
 *
 * <p>Uses Jira REST API <b>v3</b> for search / issue lookup and
 * <b>v2</b> for comment read/write (v2 returns plain text bodies,
 * making duplicate-marker detection straightforward).
 *
 * <p>Dev-status endpoints ({@code /rest/dev-status/latest/…}) are internal
 * Jira Cloud APIs also used by the Jira UI — the same Basic-Auth credentials
 * work for them.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JiraClient {

    private final RestTemplate restTemplate;
    private final JiraProperties properties;

    // ── Authentication ─────────────────────────────────────────────────────────

    private HttpHeaders headers() {
        String credentials = properties.getEmail() + ":" + properties.getApiToken();
        String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encoded);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    // ── Issue Search (API v3) ──────────────────────────────────────────────────

    /**
     * Searches for Jira issues matching the given JQL query.
     * Uses cursor-based pagination — {@code nextPageToken} must be {@code null} on the first call
     * and set to the value returned by the previous response on subsequent calls.
     *
     * @param jql           JQL query string (will be URL-encoded)
     * @param nextPageToken cursor from the previous response, or {@code null} for the first page
     * @param maxResults    page size (Jira maximum is 100)
     */
    public SearchResponse searchIssues(String jql, String nextPageToken, int maxResults) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(properties.getBaseUrl() + "/rest/api/3/search/jql")
                .queryParam("jql", jql)
                .queryParam("maxResults", maxResults)
                .queryParam("fields", "id,key,summary");
        if (nextPageToken != null) {
            builder.queryParam("nextPageToken", nextPageToken);
        }
        URI uri = builder.build().encode().toUri();
        return get(uri, SearchResponse.class);
    }

    /**
     * Fetches a single Jira issue by its key (e.g. {@code PROJ-123}).
     */
    public Issue findIssue(String issueKey) {
        String url = properties.getBaseUrl() + "/rest/api/3/issue/" + issueKey
                + "?fields=id,key,summary";
        return get(url, Issue.class);
    }

    // ── Development Status (Internal Jira Cloud API) ───────────────────────────

    /**
     * Returns the development summary counts (branches, PRs, builds) for an issue.
     *
     * @param issueId numeric Jira issue ID (not the key)
     */
    public DevelopmentSummary getDevelopmentSummary(String issueId) {
        String url = properties.getBaseUrl()
                + "/rest/dev-status/latest/issue/summary?issueId=" + issueId;
        return get(url, DevelopmentSummary.class);
    }

    /**
     * Returns the raw JSON development summary string — useful for debugging
     * the actual response structure returned by this Jira tenant.
     *
     * @param issueId numeric Jira issue ID
     */
    public String getDevelopmentSummaryRaw(String issueId) {
        String url = properties.getBaseUrl()
                + "/rest/dev-status/latest/issue/summary?issueId=" + issueId;
        return get(url, String.class);
    }

    /**
     * Returns branch detail for a Jira issue from GitLab.
     *
     * @param issueId numeric Jira issue ID
     */
    public BranchDetail getBranches(String issueId) {
        String url = properties.getBaseUrl()
                + "/rest/dev-status/latest/issue/detail"
                + "?issueId=" + issueId
                + "&applicationType=" + properties.getGitlabApplicationType()
                + "&dataType=branch";
        return get(url, BranchDetail.class);
    }

    /**
     * Returns pull-request (merge-request) detail for a Jira issue from GitLab.
     *
     * @param issueId numeric Jira issue ID
     */
    public PullRequestDetail getPullRequests(String issueId) {
        String url = properties.getBaseUrl()
                + "/rest/dev-status/latest/issue/detail"
                + "?issueId=" + issueId
                + "&applicationType=" + properties.getGitlabApplicationType()
                + "&dataType=pullrequest";
        return get(url, PullRequestDetail.class);
    }

    /** Returns the raw pull-request JSON — used for deserialization diagnostics. */
    public String getPullRequestsRaw(String issueId) {
        String url = properties.getBaseUrl()
                + "/rest/dev-status/latest/issue/detail"
                + "?issueId=" + issueId
                + "&applicationType=" + properties.getGitlabApplicationType()
                + "&dataType=pullrequest";
        return get(url, String.class);
    }

    public CommitDetail getCommits(String issueId) {
        String url = properties.getBaseUrl()
                + "/rest/dev-status/latest/issue/detail"
                + "?issueId=" + issueId
                + "&applicationType=" + properties.getGitlabApplicationType()
                + "&dataType=repository";
        return get(url, CommitDetail.class);
    }

    /** Returns the raw commit JSON — used for deserialization diagnostics. */
    public String getCommitsRaw(String issueId) {
        String url = properties.getBaseUrl()
                + "/rest/dev-status/latest/issue/detail"
                + "?issueId=" + issueId
                + "&applicationType=" + properties.getGitlabApplicationType()
                + "&dataType=repository";
        return get(url, String.class);
    }

    /**
     * Returns pipeline (build) detail for a Jira issue from all CI providers.
     * Uses the {@code cloud-providers} application type which aggregates GitLab CI
     * and other connected build systems.
     *
     * @param issueId numeric Jira issue ID
     */
    public BuildDetail getBuilds(String issueId) {
        String url = properties.getBaseUrl()
                + "/rest/dev-status/latest/issue/detail"
                + "?issueId=" + issueId
                + "&applicationType=cloud-providers"
                + "&dataType=build";
        return get(url, BuildDetail.class);
    }

    // ── Comments (API v2) ──────────────────────────────────────────────────────

    /**
     * Posts a comment to an issue using REST API v3 with Atlassian Document Format (ADF).
     * ADF is required to support features like the {@code expand} (collapsible panel) node.
     *
     * @param issueKey Jira issue key, e.g. {@code PROJ-123}
     * @param adfBody  ADF document produced by {@link eu.mxtool.jiracomments.service.CommentGeneratorService}
     */
    public void addComment(String issueKey, Map<String, Object> adfBody) {
        String url = properties.getBaseUrl()
                + "/rest/api/3/issue/" + issueKey + "/comment";
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("body", adfBody);
        executeWithRetry(() -> {
            restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers()),
                    String.class);
            return null;
        });
    }

    /**
     * Returns {@code true} if any existing comment on the issue contains
     * the duplicate-detection marker string.
     *
     * <p>Uses REST API v3 which returns comments in ADF JSON format.
     * The marker text is searched as a plain substring of the raw JSON body
     * (it appears as a JSON string value inside the ADF text nodes).
     * Paginates through all comment pages.
     *
     * @param issueKey Jira issue key
     */
    public boolean hasGeneratedComment(String issueKey) {
        int startAt = 0;
        final int pageSize = 100;
        final String marker = "Generated automatically from Jira Development panel";

        while (true) {
            String url = properties.getBaseUrl()
                    + "/rest/api/3/issue/" + issueKey + "/comment"
                    + "?startAt=" + startAt
                    + "&maxResults=" + pageSize;

            // Fetch raw JSON so we can search the ADF body for the marker text
            // without needing to fully deserialize the complex ADF structure.
            String rawJson = get(url, String.class);

            if (rawJson == null || rawJson.isBlank()) return false;
            if (rawJson.contains(marker)) return true;

            // Parse total / fetched count to decide whether to paginate.
            // We extract "total" and the number of comments in the page via
            // simple string scanning to avoid a full DTO for a rarely-needed field.
            int total = extractInt(rawJson, "\"total\"");
            int size  = extractInt(rawJson, "\"maxResults\"");
            if (total <= 0 || size <= 0) return false;

            startAt += size;
            if (startAt >= total) return false;
        }
    }

    // ── HTTP execution helpers ─────────────────────────────────────────────────

    private <T> T get(String url, Class<T> responseType) {
        return executeWithRetry(() ->
                restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        new HttpEntity<>(headers()),
                        responseType
                ).getBody()
        );
    }

    private <T> T get(URI uri, Class<T> responseType) {
        return executeWithRetry(() ->
                restTemplate.exchange(
                        uri,
                        HttpMethod.GET,
                        new HttpEntity<>(headers()),
                        responseType
                ).getBody()
        );
    }

    /**
     * Executes an HTTP action with automatic retry on HTTP 429 (Too Many Requests)
     * and 5xx (Server Error) responses using exponential back-off.
     *
     * <ul>
     *   <li>HTTP 4xx (except 429) → thrown immediately (no retry)</li>
     *   <li>HTTP 429 / 5xx → retried up to {@code maxRetries} times</li>
     * </ul>
     */
    private <T> T executeWithRetry(Callable<T> action) {
        int attempt = 0;
        while (true) {
            try {
                return action.call();
            } catch (HttpClientErrorException.TooManyRequests ex) {
                attempt++;
                if (attempt > properties.getMaxRetries()) {
                    throw new JiraClientException(
                            "Rate limited (HTTP 429) and retries exhausted after " + attempt + " attempts", ex);
                }
                long delay = properties.getRetryDelayMs() * (1L << (attempt - 1));
                log.warn("HTTP 429 Too Many Requests — retrying in {}ms (attempt {}/{})",
                        delay, attempt, properties.getMaxRetries());
                sleep(delay);
            } catch (HttpServerErrorException ex) {
                attempt++;
                if (attempt > properties.getMaxRetries()) {
                    throw new JiraClientException(
                            "Server error " + ex.getStatusCode() + " — retries exhausted after " + attempt + " attempts", ex);
                }
                long delay = properties.getRetryDelayMs() * (1L << (attempt - 1));
                log.warn("HTTP {} — retrying in {}ms (attempt {}/{})",
                        ex.getStatusCode(), delay, attempt, properties.getMaxRetries());
                sleep(delay);
            } catch (HttpClientErrorException ex) {
                // 4xx other than 429 are not transient — fail fast
                throw new JiraClientException(
                        "Client error " + ex.getStatusCode() + " calling Jira API: " + ex.getMessage(), ex);
            } catch (JiraClientException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new JiraClientException("Unexpected error calling Jira API: " + ex.getMessage(), ex);
            }
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /** Extracts the first integer value for the given JSON key from a raw JSON string. */
    private int extractInt(String json, String key) {
        int idx = json.indexOf(key);
        if (idx < 0) return 0;
        int colon = json.indexOf(':', idx + key.length());
        if (colon < 0) return 0;
        int start = colon + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        if (end == start) return 0;
        try { return Integer.parseInt(json.substring(start, end)); } catch (NumberFormatException e) { return 0; }
    }
}

