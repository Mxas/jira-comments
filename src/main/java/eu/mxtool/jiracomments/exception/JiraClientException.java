package eu.mxtool.jiracomments.exception;

/**
 * Unchecked exception thrown by {@link eu.mxtool.jiracomments.client.JiraClient}
 * when an API call fails and all retry attempts have been exhausted.
 */
public class JiraClientException extends RuntimeException {

    public JiraClientException(String message, Throwable cause) {
        super(message, cause);
    }
}

