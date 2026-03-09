package ai.pageindex.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cdimascio.dotenv.Dotenv;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LLM Chat Completions API wrapper.
 * Supports OpenAI and any OpenAI-compatible local server (Ollama, LM Studio, vLLM).
 * Also supports multi-provider rotating pool mode for free-tier rate-limit distribution.
 */
public class OpenAIClient {

    private static final String DEFAULT_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String OLLAMA_URL      = "http://localhost:11434/v1/chat/completions";
    private static final String GROQ_URL        = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GEMINI_URL      = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
    private static final String CEREBRAS_URL    = "https://api.cerebras.ai/v1/chat/completions";
    private static final String MISTRAL_URL     = "https://api.mistral.ai/v1/chat/completions";

    private static final int MAX_RETRIES = 3;
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    /** Shared thread pool for async (parallel) LLM calls. */
    public static final ExecutorService EXECUTOR =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 4);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** A single provider config: endpoint URL, API key, and model ID to use. */
    public record ProviderConfig(String url, String apiKey, String modelId) {}

    // Single-provider mode fields (null in pool mode)
    private final String apiKey;
    private final String apiUrl;
    // Multi-provider pool (null in single-provider mode)
    private final List<ProviderConfig> providerPool;
    private final AtomicInteger poolIndex = new AtomicInteger(0);

    private final int numCtx;
    private final OkHttpClient http;

    /**
     * Primary constructor. Resolution order for the endpoint:
     *   1. baseUrlOverride (from CLI --base-url or config)
     *   2. LLM_BASE_URL env var / .env file
     *   3. OpenAI default
     */
    public OpenAIClient(String baseUrlOverride, int numCtx) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        String fromEnv = dotenv.get("LLM_BASE_URL", System.getenv("LLM_BASE_URL"));
        if (baseUrlOverride != null && !baseUrlOverride.isBlank()) {
            this.apiUrl = baseUrlOverride;
        } else if (fromEnv != null && !fromEnv.isBlank()) {
            this.apiUrl = fromEnv;
        } else {
            this.apiUrl = DEFAULT_API_URL;
        }

        this.apiKey = resolveApiKeyForUrl(dotenv, this.apiUrl);
        this.providerPool = null;
        this.numCtx = numCtx;
        System.out.println("LLM endpoint: " + this.apiUrl);

        int readTimeoutSec = this.apiUrl.contains("11434") ? 300 : 120;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /** Multi-provider pool constructor — calls rotate across providers to distribute rate limits. */
    private OpenAIClient(List<ProviderConfig> providers, int numCtx) {
        this.apiUrl = null;
        this.apiKey = null;
        this.providerPool = List.copyOf(providers);
        this.numCtx = numCtx;
        System.out.println("LLM pool mode: " + providers.size() + " providers "
                + providers.stream().map(ProviderConfig::modelId).toList());
        this.http = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Build a rotating free-cloud pool from available API keys in the environment.
     * Distributes LLM calls across Groq, Cerebras, Mistral, Gemini to conserve rate limits.
     */
    public static OpenAIClient freeCloudPool() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        List<ProviderConfig> pool = new ArrayList<>();

        String groqKey = dotenv.get("GROQ_API_KEY", System.getenv("GROQ_API_KEY"));
        if (groqKey != null && !groqKey.isBlank()) {
            pool.add(new ProviderConfig(GROQ_URL, groqKey, "llama-3.3-70b-versatile"));
            pool.add(new ProviderConfig(GROQ_URL, groqKey, "llama-3.1-8b-instant"));
        }

        String cerebrasKey = dotenv.get("CEREBRAS_API_KEY", System.getenv("CEREBRAS_API_KEY"));
        if (cerebrasKey != null && !cerebrasKey.isBlank()) {
            pool.add(new ProviderConfig(CEREBRAS_URL, cerebrasKey, "llama3.3-70b"));
        }

        String mistralKey = dotenv.get("MISTRAL_API_KEY", System.getenv("MISTRAL_API_KEY"));
        if (mistralKey != null && !mistralKey.isBlank()) {
            pool.add(new ProviderConfig(MISTRAL_URL, mistralKey, "mistral-small-latest"));
        }

        String geminiKey = dotenv.get("GOOGLE_API_KEY", System.getenv("GOOGLE_API_KEY"));
        if (geminiKey != null && !geminiKey.isBlank()) {
            pool.add(new ProviderConfig(GEMINI_URL, geminiKey, "gemini-2.0-flash"));
        }

        if (pool.isEmpty()) {
            pool.add(new ProviderConfig(OLLAMA_URL, "local", "qwen2.5:7b"));
        }

        return new OpenAIClient(pool, 8192);
    }

    private static String resolveApiKeyForUrl(Dotenv dotenv, String url) {
        String key = null;
        if (url.contains("groq.com"))           key = dotenv.get("GROQ_API_KEY",       System.getenv("GROQ_API_KEY"));
        else if (url.contains("googleapis.com")) key = dotenv.get("GOOGLE_API_KEY",     System.getenv("GOOGLE_API_KEY"));
        else if (url.contains("cerebras.ai"))    key = dotenv.get("CEREBRAS_API_KEY",   System.getenv("CEREBRAS_API_KEY"));
        else if (url.contains("mistral.ai"))     key = dotenv.get("MISTRAL_API_KEY",    System.getenv("MISTRAL_API_KEY"));
        else if (url.contains("openrouter.ai"))  key = dotenv.get("OPENROUTER_API_KEY", System.getenv("OPENROUTER_API_KEY"));
        else                                      key = dotenv.get("CHATGPT_API_KEY",    System.getenv("CHATGPT_API_KEY"));
        return (key != null && !key.isBlank()) ? key : "local";
    }

    public OpenAIClient(String baseUrlOverride) { this(baseUrlOverride, 8192); }

    /** No-arg constructor — resolves endpoint from env vars / .env only. */
    public OpenAIClient() { this("", 8192); }

    /** Result record for calls that also return the finish_reason. */
    public record CompletionResult(String content, String finishReason) {}

    /**
     * Synchronous chat completion. In pool mode, rotates across providers on rate-limit errors.
     */
    public String call(String model, String prompt) {
        if (providerPool != null) return callFromPool(prompt);
        return callWithHistory(model, prompt, null);
    }

    /**
     * Synchronous chat completion with optional chat history.
     */
    public String callWithHistory(String model, String prompt, List<Map<String, String>> chatHistory) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                ArrayNode messages = buildMessages(chatHistory, prompt);
                String body = buildRequestBody(model, messages, false, apiUrl);
                JsonNode response = post(body, apiUrl, apiKey);
                return extractContent(response);
            } catch (Exception e) {
                System.out.println("************* Retrying [" + e.getMessage() + "] *************");
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
     * In pool mode, rotates across providers on rate-limit errors.
     */
    public CompletionResult callWithFinishReason(String model, String prompt, List<Map<String, String>> chatHistory) {
        if (providerPool != null) {
            String content = callFromPool(prompt);
            return new CompletionResult(content, "Error".equals(content) ? "error" : "finished");
        }
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                ArrayNode messages = buildMessages(chatHistory, prompt);
                String body = buildRequestBody(model, messages, false, apiUrl);
                JsonNode response = post(body, apiUrl, apiKey);
                String content = extractContent(response);
                String finishReason = response.get("choices").get(0).get("finish_reason").asText("stop");
                String reason = "length".equals(finishReason) ? "max_output_reached" : "finished";
                return new CompletionResult(content, reason);
            } catch (Exception e) {
                System.out.println("************* Retrying [" + e.getMessage() + "] *************");
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
     */
    public CompletableFuture<String> callAsync(String model, String prompt) {
        return CompletableFuture.supplyAsync(() -> call(model, prompt), EXECUTOR);
    }

    // -------------------------------------------------------------------------
    // Pool mode: rotate across providers, skip provider on 429
    // -------------------------------------------------------------------------

    private String callFromPool(String prompt) {
        int size = providerPool.size();
        int startIdx = poolIndex.get();
        for (int i = 0; i < size; i++) {
            int idx = (startIdx + i) % size;
            ProviderConfig p = providerPool.get(idx);
            for (int retry = 0; retry < MAX_RETRIES; retry++) {
                try {
                    ArrayNode messages = buildMessages(null, prompt);
                    String body = buildRequestBody(p.modelId(), messages, false, p.url());
                    JsonNode response = post(body, p.url(), p.apiKey());
                    poolIndex.set((idx + 1) % size); // rotate for next call
                    return extractContent(response);
                } catch (Exception e) {
                    String msg = e.getMessage() != null ? e.getMessage() : "";
                    System.out.println("[Pool] " + p.modelId() + " attempt " + retry + ": "
                            + msg.substring(0, Math.min(120, msg.length())));
                    if (msg.contains("429")) break; // rate-limited — skip to next provider
                    if (retry < MAX_RETRIES - 1) sleep(1000);
                }
            }
        }
        System.err.println("[Pool] All providers exhausted — returning Error");
        return "Error";
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

    private String buildRequestBody(String model, ArrayNode messages, boolean stream, String url) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0);
        body.put("stream", stream);
        body.set("messages", messages);
        // Ollama needs explicit context window — cloud APIs ignore this field.
        if (url != null && url.contains("11434") && numCtx > 0) {
            ObjectNode options = MAPPER.createObjectNode();
            options.put("num_ctx", numCtx);
            body.set("options", options);
        }
        return MAPPER.writeValueAsString(body);
    }

    private JsonNode post(String bodyJson, String url, String apiKey) throws IOException {
        RequestBody requestBody = RequestBody.create(bodyJson, JSON_TYPE);
        Request request = new Request.Builder()
                .url(url)
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
