package eu.mxtool.jiracomments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application entry point.
 *
 * <p>Run modes (pass as command-line arguments):
 * <ul>
 *   <li><b>Test a single issue</b> (recommended before full run):
 *       <pre>{@code java -jar jira-comments.jar --jira.project-key=MYPROJ --jira.test-issue-key=MYPROJ-123}</pre></li>
 *   <li><b>Full project backfill</b>:
 *       <pre>{@code java -jar jira-comments.jar --jira.project-key=MYPROJ}</pre></li>
 * </ul>
 *
 * <p>Infrastructure config ({@code base-url}, {@code email}, {@code api-token}) is read
 * from {@code application.yml} or environment variables ({@code JIRA_USER_EMAIL},
 * {@code JIRA_API_TOKEN}).
 *
 * @see CommandLineApp
 */
@SpringBootApplication
public class JiraCommentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(JiraCommentsApplication.class, args);
    }
}
