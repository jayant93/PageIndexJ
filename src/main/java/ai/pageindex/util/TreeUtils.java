package ai.pageindex.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tree/JSON utility functions.
 * Mirrors the tree-related and JSON helper functions in utils.py.
 *
 * All tree nodes are represented as Map<String, Object> for flat lists,
 * and ObjectNode for the final JSON tree output.
 */
public class TreeUtils {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    // -------------------------------------------------------------------------
    // JSON extraction helpers (mirrors extract_json, get_json_content)
    // -------------------------------------------------------------------------

    /**
     * Extract and parse JSON from an LLM response string.
     * Handles ```json ... ``` fences and bare JSON.
     * Mirrors extract_json(content) in utils.py.
     */
    public static JsonNode extractJson(String content) {
        if (content == null || content.isBlank()) return MAPPER.createObjectNode();
        try {
            String json = stripJsonFence(content);
            json = json.replace("None", "null")
                       .replaceAll("\\r|\\n", " ")
                       .replaceAll("\\s+", " ")
                       .trim();
            return MAPPER.readTree(json);
        } catch (Exception e) {
            // Second attempt: remove trailing commas
            try {
                String json = stripJsonFence(content)
                        .replaceAll(",\\s*]", "]")
                        .replaceAll(",\\s*}", "}");
                return MAPPER.readTree(json);
            } catch (Exception e2) {
                return MAPPER.createObjectNode();
            }
        }
    }

    /** Strip ```json ... ``` fences. Mirrors get_json_content in utils.py. */
    public static String stripJsonFence(String text) {
        if (text == null) return "";
        int start = text.indexOf("```json");
        if (start != -1) {
            start += 7;
            int end = text.lastIndexOf("```");
            if (end > start) return text.substring(start, end).strip();
        }
        return text.strip();
    }

    // -------------------------------------------------------------------------
    // physical_index conversion (mirrors convert_physical_index_to_int)
    // -------------------------------------------------------------------------

    /**
     * Convert a physical_index string like "<physical_index_5>" or "physical_index_5" to integer 5.
     * Mirrors convert_physical_index_to_int in utils.py.
     */
    public static Integer parsePhysicalIndex(String val) {
        if (val == null) return null;
        val = val.trim();
        // "<physical_index_5>" or "physical_index_5"
        Pattern p = Pattern.compile("physical_index_(\\d+)");
        Matcher m = p.matcher(val);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        // Try plain integer string
        try { return Integer.parseInt(val); } catch (NumberFormatException ignored) {}
        return null;
    }

    /**
     * Convert physical_index in all items of a flat TOC list.
     * Mirrors convert_physical_index_to_int(data) when data is List in utils.py.
     */
    @SuppressWarnings("unchecked")
    public static void convertPhysicalIndicesToInt(List<Map<String, Object>> list) {
        for (Map<String, Object> item : list) {
            Object pi = item.get("physical_index");
            if (pi instanceof String s) {
                Integer val = parsePhysicalIndex(s);
                item.put("physical_index", val);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Flat list ↔ Tree conversion (mirrors list_to_tree, post_processing)
    // -------------------------------------------------------------------------

    /**
     * Convert a flat ordered TOC list (with "structure" codes like "1", "1.1") to a nested tree.
     * Mirrors list_to_tree(data) in utils.py.
     */
    public static List<Map<String, Object>> listToTree(List<Map<String, Object>> data) {
        Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
        List<Map<String, Object>> roots = new ArrayList<>();

        for (Map<String, Object> item : data) {
            String structure = (String) item.get("structure");
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("title", item.get("title"));
            node.put("start_index", item.get("start_index"));
            node.put("end_index", item.get("end_index"));
            node.put("nodes", new ArrayList<Map<String, Object>>());
            copyIfPresent(item, node, "node_id");
            copyIfPresent(item, node, "summary");

            nodes.put(structure, node);
            String parent = parentStructure(structure);
            if (parent != null && nodes.containsKey(parent)) {
                ((List<Map<String, Object>>) nodes.get(parent).get("nodes")).add(node);
            } else {
                roots.add(node);
            }
        }

        // Clean empty nodes arrays
        for (Map<String, Object> root : roots) cleanEmptyNodes(root);
        return roots;
    }

    /** Post-process a flat verified TOC list into a tree.
     *  Sets start_index / end_index, then calls listToTree.
     *  Mirrors post_processing(structure, end_physical_index) in utils.py.
     */
    public static List<Map<String, Object>> postProcessing(
            List<Map<String, Object>> structure, int endPhysicalIndex) {

        for (int i = 0; i < structure.size(); i++) {
            Map<String, Object> item = structure.get(i);
            item.put("start_index", item.get("physical_index"));
            if (i < structure.size() - 1) {
                Map<String, Object> next = structure.get(i + 1);
                String appearStart = (String) next.getOrDefault("appear_start", "");
                int nextPhy = toInt(next.get("physical_index"), endPhysicalIndex);
                item.put("end_index", "yes".equals(appearStart) ? nextPhy - 1 : nextPhy);
            } else {
                item.put("end_index", endPhysicalIndex);
            }
        }

        List<Map<String, Object>> tree = listToTree(structure);
        if (!tree.isEmpty()) return tree;

        // Fallback: return flat structure after removing navigation fields
        for (Map<String, Object> node : structure) {
            node.remove("appear_start");
            node.remove("physical_index");
        }
        return structure;
    }

    // -------------------------------------------------------------------------
    // Tree traversal helpers
    // -------------------------------------------------------------------------

    /**
     * Flatten a nested tree into a list of all nodes (without the "nodes" key).
     * Mirrors get_nodes(structure) in utils.py.
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> getNodes(Object structure) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (structure instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) map);
            copy.remove("nodes");
            result.add(copy);
            Object nodes = ((Map<?, ?>) map).get("nodes");
            if (nodes instanceof List) result.addAll(getNodes(nodes));
        } else if (structure instanceof List<?> list) {
            for (Object item : list) result.addAll(getNodes(item));
        }
        return result;
    }

    /**
     * Flatten including the "nodes" child list.
     * Mirrors structure_to_list(structure) in utils.py.
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> structureToList(Object structure) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (structure instanceof Map<?, ?> map) {
            result.add((Map<String, Object>) map);
            Object nodes = map.get("nodes");
            if (nodes instanceof List) result.addAll(structureToList(nodes));
        } else if (structure instanceof List<?> list) {
            for (Object item : list) result.addAll(structureToList(item));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Node ID assignment (mirrors write_node_id)
    // -------------------------------------------------------------------------

    /**
     * Assign sequential node_ids (breadth-first "0001", "0002", …).
     * Mirrors write_node_id(data, node_id) in utils.py.
     */
    @SuppressWarnings("unchecked")
    public static int writeNodeId(Object data, int nodeId) {
        if (data instanceof Map<?, ?> map) {
            ((Map<String, Object>) map).put("node_id", String.format("%04d", nodeId));
            nodeId++;
            Object nodes = map.get("nodes");
            if (nodes instanceof List) nodeId = writeNodeId(nodes, nodeId);
        } else if (data instanceof List<?> list) {
            for (Object item : list) nodeId = writeNodeId(item, nodeId);
        }
        return nodeId;
    }

    // -------------------------------------------------------------------------
    // Node text helpers (mirrors add_node_text, add_node_text_with_labels)
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public static void addNodeText(Object node, List<PdfParser.PageEntry> pages) {
        if (node instanceof Map<?, ?> map) {
            int start = toInt(map.get("start_index"), 1);
            int end = toInt(map.get("end_index"), pages.size());
            ((Map<String, Object>) map).put("text", PdfParser.getTextOfPdfPages(pages, start, end));
            Object nodes = map.get("nodes");
            if (nodes instanceof List) addNodeText(nodes, pages);
        } else if (node instanceof List<?> list) {
            for (Object item : list) addNodeText(item, pages);
        }
    }

    @SuppressWarnings("unchecked")
    public static void removeNodeText(Object data) {
        if (data instanceof Map<?, ?> map) {
            ((Map<String, Object>) map).remove("text");
            Object nodes = map.get("nodes");
            if (nodes instanceof List) removeNodeText(nodes);
        } else if (data instanceof List<?> list) {
            for (Object item : list) removeNodeText(item);
        }
    }

    // -------------------------------------------------------------------------
    // Preface injection (mirrors add_preface_if_needed)
    // -------------------------------------------------------------------------

    public static List<Map<String, Object>> addPrefaceIfNeeded(List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) return data;
        Object pi = data.get(0).get("physical_index");
        if (pi instanceof Integer idx && idx > 1) {
            Map<String, Object> preface = new LinkedHashMap<>();
            preface.put("structure", "0");
            preface.put("title", "Preface");
            preface.put("physical_index", 1);
            data.add(0, preface);
        }
        return data;
    }

    // -------------------------------------------------------------------------
    // Print helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public static void printToc(List<Map<String, Object>> tree, int indent) {
        for (Map<String, Object> node : tree) {
            System.out.println("  ".repeat(indent) + node.get("title"));
            Object nodes = node.get("nodes");
            if (nodes instanceof List<?> children && !((List<?>) children).isEmpty()) {
                printToc((List<Map<String, Object>>) children, indent + 1);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Validate / truncate physical indices
    // -------------------------------------------------------------------------

    /**
     * Remove or null-out physical_index values that exceed total page count.
     * Mirrors validate_and_truncate_physical_indices in utils.py.
     */
    public static List<Map<String, Object>> validateAndTruncatePhysicalIndices(
            List<Map<String, Object>> list, int pageListLength, int startIndex) {
        int maxValid = pageListLength + startIndex - 1;
        for (Map<String, Object> item : list) {
            Object pi = item.get("physical_index");
            if (pi instanceof Integer idx && idx > maxValid) {
                item.put("physical_index", null);
            }
        }
        return list;
    }

    // -------------------------------------------------------------------------
    // Convert to Jackson ObjectNode for final serialization
    // -------------------------------------------------------------------------

    /**
     * Deep-convert a List<Map> tree to a Jackson ArrayNode for JSON output.
     */
    @SuppressWarnings("unchecked")
    public static ArrayNode toJsonArray(List<Map<String, Object>> tree) {
        ArrayNode arr = MAPPER.createArrayNode();
        for (Map<String, Object> node : tree) arr.add(toJsonNode(node));
        return arr;
    }

    @SuppressWarnings("unchecked")
    public static ObjectNode toJsonNode(Map<String, Object> map) {
        ObjectNode obj = MAPPER.createObjectNode();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (v == null) obj.putNull(k);
            else if (v instanceof String s) obj.put(k, s);
            else if (v instanceof Integer i) obj.put(k, i);
            else if (v instanceof Long l) obj.put(k, l);
            else if (v instanceof Boolean b) obj.put(k, b);
            else if (v instanceof List<?> list) {
                ArrayNode arr = MAPPER.createArrayNode();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m)
                        arr.add(toJsonNode((Map<String, Object>) m));
                }
                obj.set(k, arr);
            }
        }
        return obj;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static String parentStructure(String s) {
        if (s == null) return null;
        int last = s.lastIndexOf('.');
        return last > 0 ? s.substring(0, last) : null;
    }

    @SuppressWarnings("unchecked")
    private static void cleanEmptyNodes(Map<String, Object> node) {
        List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("nodes");
        if (children == null || children.isEmpty()) {
            node.remove("nodes");
        } else {
            for (Map<String, Object> child : children) cleanEmptyNodes(child);
        }
    }

    private static void copyIfPresent(Map<String, Object> src, Map<String, Object> dst, String key) {
        if (src.containsKey(key)) dst.put(key, src.get(key));
    }

    public static int toInt(Object val, int defaultVal) {
        if (val == null) return defaultVal;
        if (val instanceof Integer i) return i;
        if (val instanceof Long l) return l.intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception ignored) {}
        return defaultVal;
    }
}
