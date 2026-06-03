# jira-comments - API generated

> **This project was fully designed, implemented, debugged, and documented by AI tools (GitHub Copilot).**
> No manual code was written — every Java class, Gradle script, test, and this README were produced through AI-assisted pair programming sessions.

---

## What is this tool?

`jira-comments` is a command-line Java tool that **creates a Development Activity snapshot comment on every Jira issue** in a project before a Jira migration.

### Why does this exist?

When migrating Jira Cloud to a new instance (or switching Git integrations), the **Development panel** — which shows branches, commits, pull requests, and builds linked to each issue — is often lost or becomes unavailable in the target environment.

This tool reads the current development activity for every issue via the Jira internal `dev-status` API and posts a **plain-text summary comment** onto each issue before the migration. After the migration, the historical development context is preserved as a regular comment visible to everyone.

### What it posts

For each issue that has development activity the tool adds a comment that lists:

- Linked Git **branches** (repository name + branch name)
- **Pull requests** (title, state, URL)
- **Commits** (SHA, message, author, date)
- **Builds** (pipeline name, state, URL)

Issues that already have such a comment, or have no development activity at all, are silently skipped.

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ |
| Jira Cloud | Any (API v3) |
| Git integration | GitLab for Jira Cloud (OAuth app) or compatible |

---

## Environment Variables

Three secrets **must** be set as environment variables before running the tool.

### `JIRA_BASE_URL`

The base URL of your Jira Cloud instance — no trailing slash.

**Windows (Command Prompt)**
```cmd
set JIRA_BASE_URL=https://your-org.atlassian.net
```

**Windows (PowerShell)**
```powershell
$env:JIRA_BASE_URL = "https://your-org.atlassian.net"
```

**Windows (permanent — System Environment Variables)**
```powershell
[System.Environment]::SetEnvironmentVariable("JIRA_BASE_URL", "https://your-org.atlassian.net", "User")
```

**Linux / macOS (current session)**
```bash
export JIRA_BASE_URL=https://your-org.atlassian.net
```

**Linux / macOS (permanent — add to `~/.bashrc` or `~/.zshrc`)**
```bash
echo 'export JIRA_BASE_URL=https://your-org.atlassian.net' >> ~/.bashrc
source ~/.bashrc
```

---

### `JIRA_USER_EMAIL`

The e-mail address of the Jira account used for API access.

**Windows (PowerShell)**
```powershell
$env:JIRA_USER_EMAIL = "you@example.com"
```

**Linux / macOS**
```bash
export JIRA_USER_EMAIL=you@example.com
```

---

### `JIRA_API_TOKEN`

A Jira API token — **not your password**.  
Generate one at: <https://id.atlassian.com/manage-profile/security/api-tokens>

**Windows (PowerShell)**
```powershell
$env:JIRA_API_TOKEN = "your-api-token-here"
```

**Linux / macOS**
```bash
export JIRA_API_TOKEN=your-api-token-here
```

---

## Running the tool

### Using the pre-built JAR (`jira-comment-tool.jar`)

The `jira-comment-tool.jar` in the project root is a self-contained executable Spring Boot fat-jar.

```bash
java -jar jira-comment-tool.jar --jira.project-key=MYPROJ --jira.issue-keys=ALL
```

### Building from source

```bash
# Linux / macOS
./gradlew build

# Windows
gradlew.bat build
```

The JAR is automatically copied to the project root as `jira-comment-tool.jar` after every build.

---

## Parameters

All parameters are passed on the command line using `--jira.<name>=<value>`.

### Required

| Parameter | Example | Description |
|---|---|---|
| `--jira.project-key` | `--jira.project-key=MYPROJ` | Jira project key to process |
| `--jira.issue-keys` | `--jira.issue-keys=ALL` | `ALL` for a full project backfill, or a comma-separated list of specific keys |

### Optional

| Parameter | Default | Description |
|---|---|---|
| `--jira.page-size` | `50` | Number of issues fetched per API page (max `100`) |
| `--jira.start-page` | `0` | Zero-based page number to begin processing from — useful for resuming an interrupted run |
| `--jira.end-page` | `0` | Last page number to process inclusive (`0` = no limit) — use with `--jira.start-page` to process a specific range |
| `--jira.request-delay-ms` | `50` | Milliseconds to wait between successive API requests (rate-limit protection) |
| `--jira.max-retries` | `3` | Maximum retry attempts on transient HTTP `429` / `5xx` errors |
| `--jira.retry-delay-ms` | `2000` | Base delay (ms) before the first retry; doubles on each subsequent attempt (exponential back-off) |
| `--jira.max-successful-processed` | `0` | Stop after this many successfully commented issues (`0` = no limit) |

---

## Usage examples

### Full project backfill

```bash
java -jar jira-comment-tool.jar \
  --jira.project-key=MYPROJ \
  --jira.issue-keys=ALL
```

### Targeted run (specific issues only)

```bash
java -jar jira-comment-tool.jar \
  --jira.project-key=MYPROJ \
  --jira.issue-keys=MYPROJ-1,MYPROJ-42,MYPROJ-99
```

### Dry-run / preview (limit to 5 comments)

```bash
java -jar jira-comment-tool.jar \
  --jira.project-key=MYPROJ \
  --jira.issue-keys=ALL \
  --jira.max-successful-processed=5
```

### Resume from page 7 (after an interrupted run)

```bash
java -jar jira-comment-tool.jar \
  --jira.project-key=MYPROJ \
  --jira.issue-keys=ALL \
  --jira.start-page=7
```

### Process only pages 3–6 (page range)

```bash
java -jar jira-comment-tool.jar \
  --jira.project-key=MYPROJ \
  --jira.issue-keys=ALL \
  --jira.start-page=3 \
  --jira.end-page=6
```

### Windows (single line, PowerShell)

```powershell
java -jar jira-comment-tool.jar --jira.project-key=MYPROJ --jira.issue-keys=ALL --jira.max-successful-processed=5
```

---

## Output — Backfill Report

At the end of every run (including on Ctrl+C / SIGTERM) a plain-text report is written to the working directory:

```
jira-backfill-2026-06-03T12-30-00.txt
```

The report contains:

- Run mode, project, start / end times
- **Last page processed** and **last ticket processed** (useful for resuming)
- Summary counts: processed / commented / skipped / failed
- Full lists of commented, skipped, failed, and all processed issue keys

---

## License

MIT
