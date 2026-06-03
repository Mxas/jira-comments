# GitHub Copilot — Project Instructions

## Project overview

Spring Boot 4 / Java 21 command-line tool that posts a Development Activity snapshot
comment on every Jira Cloud issue before a project migration.
No web server — `spring.main.web-application-type=none`.

## Stack

| Layer | Technology |
|---|---|
| Language | Java 21 (records, sealed types, text blocks are fine) |
| Framework | Spring Boot 4.x — `CommandLineRunner`, `@ConfigurationProperties` |
| HTTP client | `RestTemplate` (no WebClient) |
| JSON | Jackson 3.x (`tools.jackson.*` packages, not `com.fasterxml.*`) |
| Build | Gradle (Groovy DSL) |
| Boilerplate | Lombok (`@Data`, `@Slf4j`, `@RequiredArgsConstructor`) |
| Tests | JUnit 5 + Mockito — unit only, no Spring context in tests |

## Key conventions

- All Jira credentials come from env vars: `JIRA_BASE_URL`, `JIRA_USER_EMAIL`, `JIRA_API_TOKEN`
- All runtime options are CLI args (`--jira.*`), **not** hardcoded in `application.yml`
- `application.yml` contains only infrastructure defaults (timeouts, retry counts)
- DTOs are Java **records** with `@JsonIgnoreProperties(ignoreUnknown = true)`
- `SearchResponse` uses cursor-based pagination (`nextPageToken`), not offset (`startAt`/`total`)
- `JiraClient.searchIssues(jql, nextPageToken, maxResults)` — second arg is `String`, nullable
- URL construction uses `UriComponentsBuilder.fromUriString(...).build().encode().toUri()`
  to avoid double-encoding; pass the resulting `URI` to `RestTemplate.exchange(URI, ...)`
- Retry logic (429 / 5xx) lives entirely inside `JiraClient.executeWithRetry()`
- `BackfillService` writes a `jira-backfill-<timestamp>.txt` report on both normal exit
  and Ctrl+C/SIGTERM (via `@PreDestroy`); `AtomicBoolean reportWritten` prevents double-write
- After `./gradlew build` the fat-jar is copied to the project root as `jira-comment-tool.jar`

## Package structure

```
eu.mxtool.jiracomments
├── JiraCommentsApplication.java   # main class
├── CommandLineApp.java            # CommandLineRunner entry point
├── client/
│   └── JiraClient.java           # all Jira REST calls + retry logic
├── config/
│   ├── AppConfig.java             # RestTemplate bean
│   └── JiraProperties.java       # @ConfigurationProperties(prefix="jira")
├── dto/                           # JSON records (SearchResponse, Issue, DevActivity, …)
├── exception/
│   └── JiraClientException.java
└── service/
    ├── BackfillService.java       # orchestration + report writing
    ├── DevActivityService.java    # fetches dev-status details
    ├── CommentGeneratorService.java
    └── AdfBuilder.java            # Atlassian Document Format helpers
```

## What NOT to do

- Do not add a web/servlet layer or any HTTP endpoints
- Do not use `com.fasterxml.jackson` — this project uses Jackson 3 (`tools.jackson`)
- Do not use `UriUtils.encode()` for building Jira search URLs — use `UriComponentsBuilder`
- Do not use `fromHttpUrl()` — it was removed in Spring 7; use `fromUriString()`
- Do not add `startAt` / `total` to `SearchResponse` — the new Jira search API uses `nextPageToken`
- Do not commit `jira-backfill-*.txt` or `jira-comment-tool.jar` (both in `.gitignore`)

