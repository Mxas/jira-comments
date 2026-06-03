package eu.mxtool.jiracomments;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import eu.mxtool.jiracomments.config.JiraProperties;
import eu.mxtool.jiracomments.service.BackfillService;

/**
 * Application entry point executed after the Spring context starts.
 *
 * <h2>Required arguments</h2>
 * <pre>{@code
 * --jira.project-key=MYPROJ
 * --jira.issue-keys=ALL | MYPROJ-1,MYPROJ-2,...
 * }</pre>
 *
 * <h2>Optional arguments</h2>
 * <ul>
 *   <li>{@code --jira.page-size=N} — issues fetched per page, max 100 (default {@code 50}).</li>
 *   <li>{@code --jira.request-delay-ms=N} — delay between API requests in ms (default {@code 50}).</li>
 *   <li>{@code --jira.start-page=N} — zero-based page number to begin processing from
 *       (default {@code 0}). Useful for resuming an interrupted run.</li>
 *   <li>{@code --jira.end-page=N} — last page number to process inclusive
 *       (default {@code 0} = no limit). Use together with {@code --jira.start-page} to process a page range.</li>
 *   <li>{@code --jira.max-successful-processed=N} — stop after N successful comments.</li>
 * </ul>
 *
 * <h2>Modes</h2>
 * <ul>
 *   <li><b>Full backfill</b> — processes every issue in the project:
 *       <pre>{@code --jira.issue-keys=ALL}</pre></li>
 *   <li><b>Targeted run</b> — processes only the listed issues:
 *       <pre>{@code --jira.issue-keys=MYPROJ-1,MYPROJ-2}</pre></li>
 * </ul>
 *
 * <h2>Examples</h2>
 * <pre>{@code
 * # Full backfill:
 * java -jar jira-comments.jar --jira.project-key=MYPROJ --jira.issue-keys=ALL
 *
 * # Targeted run:
 * java -jar jira-comments.jar --jira.project-key=MYPROJ --jira.issue-keys=MYPROJ-1,MYPROJ-2
 *
 * # With Gradle:
 * ./gradlew bootRun --args='--jira.project-key=MYPROJ --jira.issue-keys=ALL'
 * }</pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CommandLineApp implements CommandLineRunner {

    private final BackfillService backfillService;
    private final JiraProperties  properties;

    @Override
    public void run(String... args) {
        List<String> issueKeys = properties.getIssueKeys();
        boolean fullRun = issueKeys.stream().anyMatch(k -> "ALL".equalsIgnoreCase(k));

        if (fullRun) {
            log.info("Mode: FULL BACKFILL — project: {}", properties.getProjectKey());
        } else {
            log.info("Mode: TARGETED — {} issue(s): {}", issueKeys.size(), issueKeys);
        }

        backfillService.run(issueKeys);
    }
}