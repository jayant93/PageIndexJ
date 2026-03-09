package ai.pageindex.web;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import java.io.ByteArrayInputStream;
import java.util.Base64;

/**
 * Initializes Firebase and exposes a Firestore bean.
 * Returns null when FIREBASE_SERVICE_ACCOUNT is not set — triggers in-memory fallbacks.
 */
@Configuration
public class FirebaseConfig {

    @Bean
    @Nullable
    public Firestore firestore(@Value("${firebase.service.account:}") String serviceAccountBase64) {
        if (serviceAccountBase64 == null || serviceAccountBase64.isBlank()) {
            System.out.println("Firebase not configured — using in-memory fallbacks (set FIREBASE_SERVICE_ACCOUNT to enable persistence)");
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(serviceAccountBase64.trim());
            GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(decoded));
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .build());
            }
            System.out.println("Firebase connected — Firestore persistence enabled");
            return FirestoreClient.getFirestore();
        } catch (Exception e) {
            System.out.println("Firebase init failed — using in-memory fallbacks: " + e.getMessage());
            return null;
        }
    }
}
