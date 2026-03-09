package ai.pageindex.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central registry of supported models and their tier/endpoint mapping.
 * The frontend never sees raw URLs or API keys — it picks a model ID and
 * this class resolves which backend endpoint to call.
 *
 * Tiers:
 *   free       — local Ollama models, no API key, no internet
 *   free-cloud — hosted free-tier APIs (Groq, Gemini, Cerebras, Mistral)
 *   paid       — OpenAI cloud models, billed per token
 */
public class ModelRegistry {

    private static final String OLLAMA_URL    = "http://localhost:11434/v1/chat/completions";
    private static final String OPENAI_URL    = ""; // empty = reads LLM_BASE_URL env or OpenAI default
    private static final String GROQ_URL      = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GEMINI_URL    = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
    private static final String CEREBRAS_URL  = "https://api.cerebras.ai/v1/chat/completions";
    private static final String MISTRAL_URL   = "https://api.mistral.ai/v1/chat/completions";

    // Model ID → provider URL
    private static final Map<String, String> MODEL_URL = new LinkedHashMap<>();
    static {
        // Groq free-cloud models
        MODEL_URL.put("llama-3.3-70b-versatile",  GROQ_URL);
        MODEL_URL.put("gemma2-9b-it",              GROQ_URL);
        MODEL_URL.put("mixtral-8x7b-32768",        GROQ_URL);
        MODEL_URL.put("llama-3.1-8b-instant",      GROQ_URL);
        // Google Gemini free-cloud models
        MODEL_URL.put("gemini-2.0-flash",          GEMINI_URL);
        MODEL_URL.put("gemini-1.5-flash",          GEMINI_URL);
        // Cerebras free-cloud models
        MODEL_URL.put("cerebras-llama-3.3-70b",    CEREBRAS_URL);
        // Mistral free-cloud models
        MODEL_URL.put("mistral-small-latest",      MISTRAL_URL);
        MODEL_URL.put("open-mistral-nemo",         MISTRAL_URL);
        // OpenAI paid models
        MODEL_URL.put("gpt-4o-mini",               OPENAI_URL);
        MODEL_URL.put("gpt-4o",                    OPENAI_URL);
        MODEL_URL.put("gpt-4-turbo",               OPENAI_URL);
        // Ollama local models — resolved as default (not in map → OLLAMA_URL)
    }

    /** Ordered model catalog returned to the UI via GET /api/models. */
    public static List<Map<String, Object>> catalog() {
        return List.of(
            category("free", "Free — Local Models",
                    "Runs locally via Ollama · No cost · No internet required",
                model("gemma3:1b",    "Gemma 3 1B",     "Ultra-fast · ~1 GB RAM"),
                model("qwen2.5:3b",   "Qwen 2.5 3B",    "Balanced speed & quality · ~2 GB RAM"),
                model("qwen2.5:7b",   "Qwen 2.5 7B",    "Strong reasoning · ~5 GB RAM"),
                model("llama3.2",     "Llama 3.2",      "Meta's open-weight model"),
                model("mistral",      "Mistral 7B",     "Efficient instruction-following")
            ),
            category("free-cloud", "Free — Cloud Models",
                    "Hosted free-tier APIs · Requires free API key · No credit card",
                model("llama-3.3-70b-versatile", "Llama 3.3 70B (Groq)",    "Fastest free inference · groq.com"),
                model("gemma2-9b-it",            "Gemma 2 9B (Groq)",       "Google model via Groq · groq.com"),
                model("mixtral-8x7b-32768",      "Mixtral 8x7B (Groq)",     "Large context · groq.com"),
                model("llama-3.1-8b-instant",    "Llama 3.1 8B (Groq)",     "Ultra-fast small model · groq.com"),
                model("gemini-2.0-flash",        "Gemini 2.0 Flash",        "Google's fastest · aistudio.google.com"),
                model("gemini-1.5-flash",        "Gemini 1.5 Flash",        "Google's reliable · aistudio.google.com"),
                model("cerebras-llama-3.3-70b",  "Llama 3.3 70B (Cerebras)","Very fast inference · cerebras.ai"),
                model("mistral-small-latest",    "Mistral Small",           "Efficient · console.mistral.ai"),
                model("open-mistral-nemo",       "Mistral Nemo",            "12B open model · console.mistral.ai")
            ),
            category("paid", "Paid — Cloud Models",
                    "Requires OpenAI API key · Usage billed per token",
                model("gpt-4o-mini",  "GPT-4o Mini",    "Fast · cost-effective"),
                model("gpt-4o",       "GPT-4o",         "High capability"),
                model("gpt-4-turbo",  "GPT-4 Turbo",    "Most capable GPT-4")
            )
        );
    }

    /**
     * Resolve the LLM endpoint URL for a given catalog model ID.
     * URL is never exposed to the frontend.
     */
    public static String resolveBaseUrl(String model) {
        if (model == null || model.isBlank()) return OLLAMA_URL;
        // Cerebras uses "llama3.3-70b" as the actual API model ID, map here
        String lookupId = model.equals("cerebras-llama-3.3-70b") ? "cerebras-llama-3.3-70b" : model;
        return MODEL_URL.getOrDefault(lookupId, OLLAMA_URL);
    }

    /**
     * Map Cerebras catalog ID to the actual API model name Cerebras expects.
     */
    public static String resolveApiModelId(String catalogModelId) {
        if ("cerebras-llama-3.3-70b".equals(catalogModelId)) return "llama3.3-70b";
        return catalogModelId;
    }

    // ---- helpers ----

    private static Map<String, Object> category(String id, String label, String description, Map<String, Object>... models) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("label", label);
        m.put("description", description);
        m.put("models", List.of(models));
        return m;
    }

    private static Map<String, Object> model(String id, String name, String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("description", description);
        return m;
    }
}
