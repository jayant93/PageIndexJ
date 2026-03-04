package ai.pageindex.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cdimascio.dotenv.Dotenv;
import okhttp3.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI Chat Completions API wrapper.
 * Mirrors ChatGPT_API, ChatGPT_API_async, ChatGPT_API_with_finish_reason in utils.py.
 */
public class OpenAIClient {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final int MAX_RETRIES = 10;
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    /** Shared thread pool for async (parallel) LLM calls. */
    public static final ExecutorService EXECUTOR =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 4);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final OkHttpClient http;

    public OpenAIClient() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        this.apiKey = dotenv.get("CHATGPT_API_KEY", System.getenv("CHATGPT_API_KEY"));
        if (this.apiKey == null || this.apiKey.isBlank()) {
            throw new IllegalStateException("CHATGPT_API_KEY not set in .env or environment");
        }
        this.http = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /** Result record for calls that also return the finish_reason. */
    public record CompletionResult(String content, String finishReason) {}

    /**
     * Synchronous chat completion. Returns response content string.
     * Retries up to MAX_RETRIES on failure.
     */
    public String call(String model, String prompt) {
        return callWithHistory(model, prompt, null);
    }

    /**
     * Synchronous chat completion with optional chat history.
     * Mirrors ChatGPT_API(model, prompt, chat_history=...).
     */
    public String callWithHistory(String model, String prompt, List<Map<String, String>> chatHistory) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                ArrayNode messages = buildMessages(chatHistory, prompt);
                String body = buildRequestBody(model, messages, false);
                JsonNode response = post(body);
                return extractContent(response);
            } catch (Exception e) {
                System.out.println("************* Retrying *************");
                if (i < MAX_RETRIES - 1) {
                    sleep(1000);
                } else {
                    System.err.println("Max retries reached for prompt: " + prompt.substring(0, Math.min(100, prompt.length())));
                    return "Error";
                }
            }
        }
        return "Error";
    }

    /**
     * Synchronous chat completion that also returns the finish_reason.
     * Mirrors ChatGPT_API_with_finish_reason in utils.py.
     */
    public CompletionResult callWithFinishReason(String model, String prompt, List<Map<String, String>> chatHistory) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                ArrayNode messages = buildMessages(chatHistory, prompt);
                String body = buildRequestBody(model, messages, false);
                JsonNode response = post(body);
                String content = extractContent(response);
                String finishReason = response.get("choices").get(0).get("finish_reason").asText("stop");
                String reason = "length".equals(finishReason) ? "max_output_reached" : "finished";
                return new CompletionResult(content, reason);
            } catch (Exception e) {
                System.out.println("************* Retrying *************");
                if (i < MAX_RETRIES - 1) {
                    sleep(1000);
                } else {
                    return new CompletionResult("Error", "error");
                }
            }
        }
        return new CompletionResult("Error", "error");
    }

    /**
     * Async (non-blocking) call. Runs on the shared thread pool.
     * Mirrors ChatGPT_API_async in utils.py.
     */
    public CompletableFuture<String> callAsync(String model, String prompt) {
        return CompletableFuture.supplyAsync(() -> call(model, prompt), EXECUTOR);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private ArrayNode buildMessages(List<Map<String, String>> history, String userPrompt) {
        ArrayNode messages = MAPPER.createArrayNode();
        if (history != null) {
            for (Map<String, String> entry : history) {
                ObjectNode msg = MAPPER.createObjectNode();
                msg.put("role", entry.get("role"));
                msg.put("content", entry.get("content"));
                messages.add(msg);
            }
        }
        ObjectNode userMsg = MAPPER.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);
        return messages;
    }

    private String buildRequestBody(String model, ArrayNode messages, boolean stream) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0);
        body.put("stream", stream);
        body.set("messages", messages);
        return MAPPER.writeValueAsString(body);
    }

    private JsonNode post(String bodyJson) throws IOException {
        RequestBody requestBody = RequestBody.create(bodyJson, JSON_TYPE);
        Request request = new Request.Builder()
                .url(API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("OpenAI API error " + response.code() + ": " + response.body().string());
            }
            return MAPPER.readTree(response.body().string());
        }
    }

    private String extractContent(JsonNode response) {
        return response.get("choices").get(0).get("message").get("content").asText();
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}
