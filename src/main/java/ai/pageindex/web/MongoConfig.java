package ai.pageindex.web;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the MongoClient bean only when the URI is a valid MongoDB connection string.
 * Spring Data MongoDB's auto-configuration is @ConditionalOnBean(MongoClient.class),
 * so when this bean is absent (invalid/missing URI) the entire MongoDB stack —
 * including repositories — is silently skipped and the app starts with in-memory fallbacks.
 */
@Configuration
@Conditional(MongoUriValidCondition.class)
public class MongoConfig {

    @Bean
    public MongoClient mongoClient(@Value("${spring.data.mongodb.uri}") String uri) {
        System.out.println("MongoDB enabled: connecting to " +
                uri.replaceAll(":[^@/]+@", ":***@")); // mask password in logs
        return MongoClients.create(uri);
    }
}
