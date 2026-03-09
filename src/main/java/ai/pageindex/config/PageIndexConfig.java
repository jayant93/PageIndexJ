package ai.pageindex.config;

/**
 * Configuration for PageIndex.
 * Mirrors config.yaml / Python's SimpleNamespace config object.
 */
public class PageIndexConfig {

    public String model = "gpt-4o-2024-11-20";
    public int tocCheckPageNum = 20;
    public int maxPageNumEachNode = 10;
    public int maxTokenNumEachNode = 20000;
    public String ifAddNodeId = "yes";
    public String ifAddNodeSummary = "yes";
    public String ifAddDocDescription = "no";
    public String ifAddNodeText = "no";
    /** LLM endpoint URL. Empty = fall back to LLM_BASE_URL env var or OpenAI default. */
    public String baseUrl = "";
    /** Ollama context window size. 0 = use OpenAIClient default (8192). */
    public int numCtx = 0;

    public PageIndexConfig() {}

    /** Convenience copy constructor for overriding individual fields. */
    public PageIndexConfig(PageIndexConfig base) {
        this.model = base.model;
        this.tocCheckPageNum = base.tocCheckPageNum;
        this.maxPageNumEachNode = base.maxPageNumEachNode;
        this.maxTokenNumEachNode = base.maxTokenNumEachNode;
        this.ifAddNodeId = base.ifAddNodeId;
        this.ifAddNodeSummary = base.ifAddNodeSummary;
        this.ifAddDocDescription = base.ifAddDocDescription;
        this.ifAddNodeText = base.ifAddNodeText;
        this.baseUrl = base.baseUrl;
        this.numCtx = base.numCtx;
    }
}
