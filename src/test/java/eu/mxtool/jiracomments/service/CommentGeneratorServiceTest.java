package eu.mxtool.jiracomments.service;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import eu.mxtool.jiracomments.dto.DevActivity;
import eu.mxtool.jiracomments.dto.DevActivity.BranchInfo;
import eu.mxtool.jiracomments.dto.DevActivity.BuildInfo;

class CommentGeneratorServiceTest {

    private final CommentGeneratorService generator = new CommentGeneratorService();
    private final ObjectMapper mapper = new ObjectMapper();

    private static final BuildInfo SAMPLE_BUILD = new BuildInfo(
            "PROJ-1/feature", 42, "https://gitlab.com/org/repo/-/pipelines/42",
            "successful", "2026-06-03T10:00:00Z", 3, 3);

    // ── Helper factories ───────────────────────────────────────────────────────

    private DevActivity activityWithBranch(String branchName) {
        return new DevActivity(
                List.of(new BranchInfo("org/repo", "https://gitlab.com/org/repo", branchName)),
                List.of(), List.of(), List.of());
    }

    private DevActivity.PrInfo simplePr(String id, String title, String status, String url) {
        return new DevActivity.PrInfo(id, title, status, url, null, null, List.of(), null);
    }

    private List<DevActivity.CommitInfo> buildCommitList(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new DevActivity.CommitInfo(
                        String.format("commit-%02d", i), "Message " + i, null, false, null, 0, null))
                .toList();
    }

    /** Serialise the ADF map to a JSON string for assertion. */
    private String json(String issueKey, DevActivity activity) {
        try {
            return mapper.writeValueAsString(generator.generate(issueKey, activity));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    void generatedComment_alwaysContainsMarker() {
        assertThat(json("PROJ-1", activityWithBranch("feature/PROJ-1-test")))
                .contains(CommentGeneratorService.DUPLICATE_MARKER);
    }

    @Test
    void generatedComment_usesH1ForTitle() {
        String j = json("PROJ-1", activityWithBranch("feature/x"));
        assertThat(j).contains("\"level\":1");
        assertThat(j).contains("Git Activity Summary");
    }

    @Test
    void generatedComment_usesExpandForBranches() {
        String j = json("PROJ-10", activityWithBranch("feature/PROJ-10-payment"));
        assertThat(j).contains("\"type\":\"expand\"");
        assertThat(j).contains("Branches (1)");
        assertThat(j).contains("Repository");
        assertThat(j).contains("Branch Name");
        assertThat(j).contains("feature/PROJ-10-payment");
    }

    @Test
    void generatedComment_omitsBranchSection_whenNoBranches() {
        DevActivity activity = new DevActivity(
                List.of(),
                List.of(simplePr("1", "My PR", "OPEN", "https://gitlab.com/org/repo/-/merge_requests/1")),
                List.of(), List.of());
        assertThat(json("PROJ-2", activity)).doesNotContain("Branches");
    }

    @Test
    void generatedComment_usesExpandForMergeRequests() {
        DevActivity activity = new DevActivity(
                List.of(),
                List.of(new DevActivity.PrInfo("my-repo!42", "Payment Fix", "OPEN",
                        "https://gitlab.com/org/my-repo/-/merge_requests/42",
                        "my-repo", "https://gitlab.com/org/my-repo",
                        List.of(), null)),
                List.of(), List.of());
        String j = json("PROJ-3", activity);
        assertThat(j).contains("Merge Requests (1)");
        assertThat(j).contains("!42");
        assertThat(j).contains("Payment Fix");
        assertThat(j).contains("OPEN");
    }

    @Test
    void generatedComment_usesExpandForCommits() {
        List<DevActivity.CommitInfo> commits = List.of(
                new DevActivity.CommitInfo("abc1234", "Fix payment",
                        "https://gitlab.com/org/repo/-/commit/abc1234", false, "2026-06-03T10:00:00Z", 3, "Jane Doe")
        );
        DevActivity activity = new DevActivity(List.of(), List.of(),
                List.of(new DevActivity.RepoCommits("org/repo", "https://gitlab.com/org/repo", commits)),
                List.of());
        String j = json("PROJ-4", activity);
        assertThat(j).contains("Commits (1)");
        assertThat(j).contains("abc1234");
        assertThat(j).contains("Fix payment");
        assertThat(j).contains("Jane Doe");
    }

    @Test
    void generatedComment_usesExpandForPipelines() {
        DevActivity activity = new DevActivity(List.of(), List.of(), List.of(),
                List.of(SAMPLE_BUILD));
        String j = json("PROJ-5", activity);
        assertThat(j).contains("Pipelines (1)");
        assertThat(j).contains("PROJ-1/feature");
        assertThat(j).contains("#42");
        assertThat(j).contains("✅");
        assertThat(j).contains("3 / 3 passed");
    }

    @Test
    void generatedComment_showsCorrectStateIcons() {
        BuildInfo failed    = new BuildInfo("branch", 1, "https://gitlab.com/org/repo/-/pipelines/1", "failed",    null, 0, 5);
        BuildInfo cancelled = new BuildInfo("branch", 2, "https://gitlab.com/org/repo/-/pipelines/2", "cancelled", null, 0, 0);
        DevActivity activity = new DevActivity(List.of(), List.of(), List.of(),
                List.of(failed, cancelled));
        String j = json("PROJ-5", activity);
        assertThat(j).contains("❌");
        assertThat(j).contains("⚠️");
    }

    @Test
    void generatedComment_omitsPipelinesSection_whenNoBuilds() {
        assertThat(json("PROJ-6", activityWithBranch("feature/PROJ-6-branch")))
                .doesNotContain("Pipelines");
    }

    @Test
    void generatedComment_truncatesCommitsAt20() {
        List<DevActivity.CommitInfo> commits = buildCommitList(25);
        DevActivity activity = new DevActivity(List.of(), List.of(),
                List.of(new DevActivity.RepoCommits("org/repo", null, commits)), List.of());
        String j = json("PROJ-7", activity);
        assertThat(j).contains("Commits (25)");
        assertThat(j).contains("5 more commits");
        assertThat(j).contains("commit-00");
        assertThat(j).contains("commit-19");
        assertThat(j).doesNotContain("commit-20");
    }

    @Test
    void generatedComment_doesNotTruncate_whenCommitsAtLimit() {
        List<DevActivity.CommitInfo> commits = buildCommitList(20);
        DevActivity activity = new DevActivity(List.of(), List.of(),
                List.of(new DevActivity.RepoCommits("org/repo", null, commits)), List.of());
        assertThat(json("PROJ-8", activity)).doesNotContain("more commits");
    }

    @Test
    void generatedComment_containsAllSections_whenFullActivity() {
        DevActivity activity = new DevActivity(
                List.of(new BranchInfo("org/repo", "https://gitlab.com/org/repo", "feature/PROJ-9-full")),
                List.of(new DevActivity.PrInfo("org/repo!99", "Full Feature", "MERGED",
                        "https://gitlab.com/org/repo/-/merge_requests/99",
                        "org/repo", "https://gitlab.com/org/repo", List.of(), null)),
                List.of(new DevActivity.RepoCommits("repo", "https://gitlab.com/org/repo",
                        List.of(new DevActivity.CommitInfo("aaa0000", "Initial commit", null, false, null, 0, null)))),
                List.of(SAMPLE_BUILD)
        );
        String j = json("PROJ-9", activity);
        assertThat(j)
                .contains("Git Activity Summary")
                .contains("Branches (1)")
                .contains("Merge Requests (1)")
                .contains("Commits (1)")
                .contains("Pipelines (1)")
                .contains(CommentGeneratorService.DUPLICATE_MARKER);
    }

    @Test
    void mergeRequest_displaysShortMrNumber() {
        DevActivity activity = new DevActivity(List.of(),
                List.of(new DevActivity.PrInfo("org/my-api!269",
                        "PROJ-286 update dependencies", "MERGED",
                        "https://gitlab.com/org/my-api/-/merge_requests/269",
                        "my-api", "https://gitlab.com/org/my-api",
                        List.of("Alice", "Bob"), "2026-06-01T08:00:00Z")),
                List.of(), List.of());
        String j = json("PROJ-286", activity);
        assertThat(j).contains("!269");
        assertThat(j).contains("PROJ-286 update dependencies");
        assertThat(j).contains("MERGED");
        assertThat(j).contains("Alice, Bob");
    }

    @Test
    void branches_repoIsLinked() {
        String j = json("PROJ-1", activityWithBranch("feature/PROJ-1-pay"));
        assertThat(j).contains("org/repo");
        assertThat(j).contains("https://gitlab.com/org/repo");
        assertThat(j).contains("feature/PROJ-1-pay");
    }
}
