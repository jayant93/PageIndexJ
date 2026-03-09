package ai.pageindex.web;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.lang.NonNull;

/**
 * True only when spring.data.mongodb.uri is a valid MongoDB connection string.
 * Prevents startup failure when MONGODB_URI is missing, empty, or wrongly set.
 */
public class MongoUriValidCondition implements Condition {

    @Override
    public boolean matches(@NonNull ConditionContext context, @NonNull AnnotatedTypeMetadata metadata) {
        String uri = context.getEnvironment()
                .getProperty("spring.data.mongodb.uri", "");
        return uri.startsWith("mongodb://") || uri.startsWith("mongodb+srv://");
    }
}
