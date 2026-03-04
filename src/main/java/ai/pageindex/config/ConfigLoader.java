package ai.pageindex.config;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;

/**
 * Loads PageIndexConfig from config.yaml, merging user overrides on top of defaults.
 * Mirrors Python's ConfigLoader class in utils.py.
 */
public class ConfigLoader {

    private static final Set<String> KNOWN_KEYS = Set.of(
            "model", "toc_check_page_num", "max_page_num_each_node",
            "max_token_num_each_node", "if_add_node_id", "if_add_node_summary",
            "if_add_doc_description", "if_add_node_text"
    );

    private final Map<String, Object> defaults;

    public ConfigLoader() {
        Yaml yaml = new Yaml();
        try (InputStream in = ConfigLoader.class.getClassLoader()
                .getResourceAsStream("config.yaml")) {
            if (in == null) throw new IllegalStateException("config.yaml not found on classpath");
            defaults = yaml.load(in);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config.yaml", e);
        }
    }

    /**
     * Load config with default values; user overrides (non-null fields) take precedence.
     */
    public PageIndexConfig load(PageIndexConfig userOpt) {
        PageIndexConfig cfg = new PageIndexConfig();

        // Apply defaults
        if (defaults.containsKey("model"))
            cfg.model = (String) defaults.get("model");
        if (defaults.containsKey("toc_check_page_num"))
            cfg.tocCheckPageNum = toInt(defaults.get("toc_check_page_num"));
        if (defaults.containsKey("max_page_num_each_node"))
            cfg.maxPageNumEachNode = toInt(defaults.get("max_page_num_each_node"));
        if (defaults.containsKey("max_token_num_each_node"))
            cfg.maxTokenNumEachNode = toInt(defaults.get("max_token_num_each_node"));
        if (defaults.containsKey("if_add_node_id"))
            cfg.ifAddNodeId = (String) defaults.get("if_add_node_id");
        if (defaults.containsKey("if_add_node_summary"))
            cfg.ifAddNodeSummary = (String) defaults.get("if_add_node_summary");
        if (defaults.containsKey("if_add_doc_description"))
            cfg.ifAddDocDescription = (String) defaults.get("if_add_doc_description");
        if (defaults.containsKey("if_add_node_text"))
            cfg.ifAddNodeText = (String) defaults.get("if_add_node_text");

        // Apply user overrides
        if (userOpt != null) {
            if (userOpt.model != null) cfg.model = userOpt.model;
            if (userOpt.tocCheckPageNum > 0) cfg.tocCheckPageNum = userOpt.tocCheckPageNum;
            if (userOpt.maxPageNumEachNode > 0) cfg.maxPageNumEachNode = userOpt.maxPageNumEachNode;
            if (userOpt.maxTokenNumEachNode > 0) cfg.maxTokenNumEachNode = userOpt.maxTokenNumEachNode;
            if (userOpt.ifAddNodeId != null) cfg.ifAddNodeId = userOpt.ifAddNodeId;
            if (userOpt.ifAddNodeSummary != null) cfg.ifAddNodeSummary = userOpt.ifAddNodeSummary;
            if (userOpt.ifAddDocDescription != null) cfg.ifAddDocDescription = userOpt.ifAddDocDescription;
            if (userOpt.ifAddNodeText != null) cfg.ifAddNodeText = userOpt.ifAddNodeText;
        }

        return cfg;
    }

    /** Load with all defaults. */
    public PageIndexConfig load() {
        return load(null);
    }

    private static int toInt(Object val) {
        if (val instanceof Integer) return (Integer) val;
        if (val instanceof Long) return ((Long) val).intValue();
        return Integer.parseInt(val.toString());
    }
}
