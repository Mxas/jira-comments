package eu.mxtool.jiracomments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import eu.mxtool.jiracomments.client.JiraClient;
import eu.mxtool.jiracomments.config.JiraProperties;
import eu.mxtool.jiracomments.dto.DevActivity;
import eu.mxtool.jiracomments.dto.Issue;
import eu.mxtool.jiracomments.dto.IssueFields;
import eu.mxtool.jiracomments.dto.SearchResponse;

/**
 * Unit tests for {@link BackfillService}.
 *
 * <p>All external collaborators (JiraClient, DevActivityService, CommentGeneratorService)
 * are mocked, so tests execute without any network calls.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BackfillServiceTest {

    @Mock JiraClient              jiraClient;
    @Mock DevActivityService      devActivityService;
    @Mock CommentGeneratorService commentGeneratorService;
    @Mock JiraProperties          properties;

    @InjectMocks BackfillService backfillService;

    private static final Issue ISSUE_1 =
            new Issue("10001", "PROJ-1", new IssueFields("Issue one"));
    private static final Issue ISSUE_2 =
            new Issue("10002", "PROJ-2", new IssueFields("Issue two"));

    private static final DevActivity SOME_ACTIVITY = new DevActivity(
            List.of(new DevActivity.BranchInfo("org/repo", "https://gitlab.com/org/repo", "feature/PROJ-1-branch")),
            List.of(),
            List.of(new DevActivity.RepoCommits("org/repo", null,
                    List.of(new DevActivity.CommitInfo("abc1234", "Fix something", null, false, null, 0, null)))),
            List.of()
    );

    private static final DevActivity EMPTY_ACTIVITY = new DevActivity(
            List.of(), List.of(), List.of(), List.of());

    @BeforeEach
    void setUp() {
        when(properties.getProjectKey()).thenReturn("PROJ");
        when(properties.getPageSize()).thenReturn(50);
        when(properties.getRequestDelayMs()).thenReturn(0L); // no delay in tests
    }

    // ── Test mode (multiple issues) ────────────────────────────────────────────

    @Test
    void targetedMode_processesOnlyTheSpecifiedIssues() {
        when(jiraClient.findIssue("PROJ-1")).thenReturn(ISSUE_1);
        when(jiraClient.hasGeneratedComment("PROJ-1")).thenReturn(false);
        when(devActivityService.getActivity("10001")).thenReturn(SOME_ACTIVITY);
        Map<String, Object> fakeAdf = Map.of("type", "doc");
        when(commentGeneratorService.generate(anyString(), any())).thenReturn(fakeAdf);

        backfillService.run(List.of("PROJ-1"));

        verify(jiraClient, times(1)).findIssue("PROJ-1");
        verify(jiraClient, times(1)).addComment("PROJ-1", fakeAdf);
        verify(jiraClient, never()).searchIssues(anyString(), nullable(String.class), anyInt());
    }

    @Test
    void targetedMode_processesMultipleSpecifiedIssues() {
        when(jiraClient.findIssue("PROJ-1")).thenReturn(ISSUE_1);
        when(jiraClient.findIssue("PROJ-2")).thenReturn(ISSUE_2);
        when(jiraClient.hasGeneratedComment(anyString())).thenReturn(false);
        when(devActivityService.getActivity(anyString())).thenReturn(SOME_ACTIVITY);
        Map<String, Object> fakeAdf = Map.of("type", "doc");
        when(commentGeneratorService.generate(anyString(), any())).thenReturn(fakeAdf);

        backfillService.run(List.of("PROJ-1", "PROJ-2"));

        verify(jiraClient, times(1)).findIssue("PROJ-1");
        verify(jiraClient, times(1)).findIssue("PROJ-2");
        verify(jiraClient, times(2)).addComment(anyString(), eq(fakeAdf));
        verify(jiraClient, never()).searchIssues(anyString(), nullable(String.class), anyInt());
    }

    // ── Duplicate detection ────────────────────────────────────────────────────

    @Test
    void fullRun_skipsIssue_whenGeneratedCommentAlreadyExists() {
        SearchResponse page = new SearchResponse(List.of(ISSUE_1), null);
        when(jiraClient.searchIssues(anyString(), nullable(String.class), anyInt())).thenReturn(page);
        when(jiraClient.hasGeneratedComment("PROJ-1")).thenReturn(true);

        backfillService.run(List.of("ALL"));

        verify(jiraClient, never()).addComment(anyString(), any());
        verify(devActivityService, never()).getActivity(anyString());
    }

    // ── No development activity ────────────────────────────────────────────────

    @Test
    void fullRun_skipsIssue_whenNoActivityFound() {
        SearchResponse page = new SearchResponse(List.of(ISSUE_1), null);
        when(jiraClient.searchIssues(anyString(), nullable(String.class), anyInt())).thenReturn(page);
        when(jiraClient.hasGeneratedComment("PROJ-1")).thenReturn(false);
        when(devActivityService.getActivity("10001")).thenReturn(EMPTY_ACTIVITY);

        backfillService.run(List.of("ALL"));

        verify(jiraClient, never()).addComment(anyString(), any());
        verify(commentGeneratorService, never()).generate(anyString(), any());
    }

    // ── Happy-path comment creation ────────────────────────────────────────────

    @Test
    void fullRun_addsComment_whenActivityPresent() {
        SearchResponse page = new SearchResponse(List.of(ISSUE_1), null);
        when(jiraClient.searchIssues(anyString(), nullable(String.class), anyInt())).thenReturn(page);
        when(jiraClient.hasGeneratedComment("PROJ-1")).thenReturn(false);
        when(devActivityService.getActivity("10001")).thenReturn(SOME_ACTIVITY);
        Map<String, Object> fakeAdf = Map.of("type", "doc", "version", 1);
        when(commentGeneratorService.generate(anyString(), any())).thenReturn(fakeAdf);

        backfillService.run(List.of("ALL"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jiraClient).addComment(anyString(), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).isEqualTo(fakeAdf);
    }

    // ── Resilience ────────────────────────────────────────────────────────────

    @Test
    void fullRun_continuesProcessing_whenOneIssueFails() {
        SearchResponse page = new SearchResponse(List.of(ISSUE_1, ISSUE_2), null);
        when(jiraClient.searchIssues(anyString(), nullable(String.class), anyInt())).thenReturn(page);

        when(jiraClient.hasGeneratedComment("PROJ-1"))
                .thenThrow(new RuntimeException("Simulated failure"));
        when(jiraClient.hasGeneratedComment("PROJ-2")).thenReturn(false);
        when(devActivityService.getActivity("10002")).thenReturn(SOME_ACTIVITY);
        Map<String, Object> fakeAdf = Map.of("type", "doc");
        when(commentGeneratorService.generate(anyString(), any())).thenReturn(fakeAdf);

        backfillService.run(List.of("ALL"));

        verify(jiraClient, never()).addComment(eq("PROJ-1"), any());
        verify(jiraClient).addComment("PROJ-2", fakeAdf);
    }

    // ── Pagination ─────────────────────────────────────────────────────────────

    @Test
    void fullRun_paginatesThroughMultiplePages() {
        // page1 has a nextPageToken → triggers second request; page2 has null → stops
        SearchResponse page1 = new SearchResponse(List.of(ISSUE_1), "page2token");
        SearchResponse page2 = new SearchResponse(List.of(ISSUE_2), null);
        when(properties.getPageSize()).thenReturn(1);
        when(jiraClient.searchIssues(anyString(), isNull(), anyInt())).thenReturn(page1);
        when(jiraClient.searchIssues(anyString(), eq("page2token"), anyInt())).thenReturn(page2);
        when(jiraClient.hasGeneratedComment(anyString())).thenReturn(true); // skip both

        backfillService.run(List.of("ALL"));

        verify(jiraClient, times(2)).searchIssues(anyString(), nullable(String.class), anyInt());
    }
}
