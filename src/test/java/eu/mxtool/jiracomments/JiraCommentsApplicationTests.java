package eu.mxtool.jiracomments;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import eu.mxtool.jiracomments.service.BackfillService;

/**
 * Smoke test — verifies the Spring application context loads without errors.
 *
 * <p>{@link BackfillService} is mocked so the {@link org.springframework.boot.CommandLineRunner} does not
 * attempt any real Jira API calls during the test.
 * The {@code JIRA_API_TOKEN} property is satisfied by a dummy value.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "jira.api-token=test-token",
                "jira.email=test@example.com",
                "jira.project-key=TEST",
                "jira.issue-keys=ALL"
        }
)
class JiraCommentsApplicationTests {

    /** Prevents CommandLineApp from triggering a real backfill on context startup. */
    @MockitoBean
    BackfillService backfillService;

    @Autowired
    CommandLineApp commandLineApp;

    @Test
    void contextLoads() {
        assertThat(commandLineApp).isNotNull();
    }
}
