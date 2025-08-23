package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "indexing")
public class IndexingConfig {
    private String userAgent;
    private String referrer;
    private int minDelayMillis;
    private int maxDelayMillis;
    private HashSet<SiteConfig> sites;
}
