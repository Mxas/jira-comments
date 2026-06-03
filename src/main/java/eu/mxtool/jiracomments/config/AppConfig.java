package eu.mxtool.jiracomments.config;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Spring application configuration – registers shared infrastructure beans.
 */
@Configuration
@EnableConfigurationProperties(JiraProperties.class)
public class AppConfig {

    /**
     * Pre-configured {@link RestTemplate} with sensible connection / read timeouts.
     * Retry logic is handled inside {@link eu.mxtool.jiracomments.client.JiraClient}
     * so we keep this bean simple and stateless.
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(30).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(60).toMillis());
        return new RestTemplate(factory);
    }
}
