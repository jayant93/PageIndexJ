package ai.pageindex.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OpenAIClient class.
 * Tests API client initialization and error handling.
 * Note: Actual API calls are skipped if CHATGPT_API_KEY is not set.
 */
class OpenAIClientTest {

    @Test
    void testCompletionResultRecord() {
        OpenAIClient.CompletionResult result = 
            new OpenAIClient.CompletionResult("Response text", "stop");
        
        assertEquals("Response text", result.content());
        assertEquals("stop", result.finishReason());
    }

    @Test
    void testCompletionResultEquality() {
        OpenAIClient.CompletionResult result1 = 
            new OpenAIClient.CompletionResult("Text", "stop");
        OpenAIClient.CompletionResult result2 = 
            new OpenAIClient.CompletionResult("Text", "stop");
        
        assertEquals(result1, result2);
    }

    @Test
    void testCompletionResultInequality() {
        OpenAIClient.CompletionResult result1 = 
            new OpenAIClient.CompletionResult("Text1", "stop");
        OpenAIClient.CompletionResult result2 = 
            new OpenAIClient.CompletionResult("Text2", "stop");
        
        assertNotEquals(result1, result2);
    }

    @Test
    void testCompletionResultDifferentFinishReason() {
        OpenAIClient.CompletionResult result1 = 
            new OpenAIClient.CompletionResult("Text", "stop");
        OpenAIClient.CompletionResult result2 = 
            new OpenAIClient.CompletionResult("Text", "max_tokens");
        
        assertNotEquals(result1, result2);
    }

    @Test
    void testCompletionResultNullContent() {
        OpenAIClient.CompletionResult result = 
            new OpenAIClient.CompletionResult(null, "error");
        
        assertNull(result.content());
        assertEquals("error", result.finishReason());
    }

    @Test
    void testClientInitializationWithoutApiKey() {
        // If API key is not set, initialization should fail
        // This test assumes CHATGPT_API_KEY is not set in test environment
        if (System.getenv("CHATGPT_API_KEY") == null) {
            assertThrows(IllegalStateException.class, OpenAIClient::new);
        }
    }

    @Test
    void testCompletionResultHashCode() {
        OpenAIClient.CompletionResult result1 = 
            new OpenAIClient.CompletionResult("Text", "stop");
        OpenAIClient.CompletionResult result2 = 
            new OpenAIClient.CompletionResult("Text", "stop");
        
        assertEquals(result1.hashCode(), result2.hashCode());
    }

    @Test
    void testCompletionResultToString() {
        OpenAIClient.CompletionResult result = 
            new OpenAIClient.CompletionResult("Response", "stop");
        
        String str = result.toString();
        assertNotNull(str);
        assertTrue(str.contains("Response") || str.contains("CompletionResult"));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CHATGPT_API_KEY", matches = ".+")
    void testClientInitializationWithApiKey() {
        assertDoesNotThrow(OpenAIClient::new);
    }
}
