package eu.mxtool.jiracomments.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly-typed configuration properties for the Jira / backfill integration.
 *
 * <p>Infrastructure settings ({@code base-url}, {@code email}, {@code api-token}, etc.)
 * are bound from the {@code jira.*} namespace in {@code application.yml}.
 *
 * <p>Runtime arguments must be supplied on the command line and are <em>not</em> present
 * in {@code application.yml}:
 * <ul>
 *   <li>{@code --jira.project-key=MYPROJ} — <b>required</b></li>
 *   <li>{@code --jira.issue-keys=ALL} — <b>required</b>; use {@code ALL} for a full project
 *       backfill, or provide a comma-separated list of specific keys (e.g.
 *       {@code MYPROJ-1,MYPROJ-2}) to process only those issues.</li>
 * </ul>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "jira")
public class JiraProperties {

    /** Base URL of the Jira Cloud instance, e.g. https://your-org.atlassian.net */
    private String baseUrl;

    /** Jira account e-mail address used for Basic Auth. */
    private String email;

    /** Jira API token (pass via JIRA_API_TOKEN env-var). */
    private String apiToken;

    /**
     * Jira project key to backfill.
     * <b>Required — pass as {@code --jira.project-key=MYPROJ} on the command line.</b>
     */
    @NotBlank(message = "--jira.project-key is required. Example: --jira.project-key=MYPROJ")
    private String projectKey;

    /** Number of issues fetched per search page (max 100 allowed by Jira).
     * Override on the command line: {@code --jira.page-size=100}. Default is {@code 50}. */
    private int pageSize = 50;

    /** Milliseconds to wait between successive API requests (rate limiting).
     * Override on the command line: {@code --jira.request-delay-ms=200}. Default is {@code 50}. */
    private long requestDelayMs = 50;

    /** Maximum number of retry attempts on transient HTTP errors (4xx 429 / 5xx). */
    private int maxRetries = 3;

    /**
     * Base delay in milliseconds before the first retry.
     * Each subsequent attempt doubles this value (exponential back-off).
     */
    private long retryDelayMs = 2000;

    /**
     * The {@code applicationType} parameter required by the Jira dev-status detail endpoint.
     * For GitLab for Jira Cloud (OAuth app), the value is
     * {@code oAuth-gitlab-jira-connect-gitlab.com}.
     * Override if your tenant uses a self-managed GitLab instance.
     */
    private String gitlabApplicationType = "oAuth-gitlab-jira-connect-gitlab.com";

    /**
     * Issue keys to process — <b>required</b>.
     * <ul>
     *   <li>Use {@code ALL} to run a full project backfill:
     *       {@code --jira.issue-keys=ALL}</li>
     *   <li>Use a comma-separated list for targeted processing:
     *       {@code --jira.issue-keys=MYPROJ-1,MYPROJ-2}</li>
     * </ul>
     */
    @NotEmpty(message = "--jira.issue-keys is required. Use ALL for a full backfill or provide specific keys, e.g. --jira.issue-keys=MYPROJ-1,MYPROJ-2")
    private List<String> issueKeys;

    /**
     * Page number to start processing from (1-based, inclusive).
     * <ul>
     *   <li>{@code 1} (default) — start from the very first page.</li>
     *   <li>{@code 12} — jump directly to page 12, skipping pages 1–11.</li>
     * </ul>
     * Example: {@code --jira.start-page=12}
     */
    private int startPage = 1;

    /**
     * Last page number to process (1-based, inclusive).
     * Processing stops after this page even if more pages are available.
     * <ul>
     *   <li>{@code 0} (default) — no limit, process all pages.</li>
     *   <li>{@code 12} — stop after page 12.</li>
     * </ul>
     * Example: {@code --jira.end-page=12}
     */
    private int endPage = 0;

    /**
     * Optional cap on the number of successfully commented issues per run.
     * Once this many comments have been posted the run stops — remaining issues
     * are not processed.
     * <ul>
     *   <li>{@code 0} (default) — no limit, process everything.</li>
     *   <li>Any positive value — stop after that many successful comments.</li>
     * </ul>
     * Example: {@code --jira.max-successful-processed=5}
     */
    private int maxSuccessfulProcessed = 0;
}

