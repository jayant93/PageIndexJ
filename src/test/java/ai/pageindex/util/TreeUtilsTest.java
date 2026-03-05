package ai.pageindex.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TreeUtils class.
 * Tests JSON parsing, physical index conversion, and tree utilities.
 */
class TreeUtilsTest {

    @Test
    void testExtractJsonFromFenced() {
        String content = "```json\n{\"name\": \"test\", \"value\": 42}\n```";
        JsonNode result = TreeUtils.extractJson(content);
        
        assertEquals("test", result.get("name").asText());
        assertEquals(42, result.get("value").asInt());
    }

    @Test
    void testExtractJsonBare() {
        String content = "{\"key\": \"value\"}";
        JsonNode result = TreeUtils.extractJson(content);
        
        assertEquals("value", result.get("key").asText());
    }

    @Test
    void testExtractJsonWithNone() {
        String content = "{\"value\": None}";
        JsonNode result = TreeUtils.extractJson(content);
        
        assertTrue(result.get("value").isNull());
    }

    @Test
    void testExtractJsonEmpty() {
        JsonNode result = TreeUtils.extractJson("");
        
        assertTrue(result.isObject());
        assertEquals(0, result.size());
    }

    @Test
    void testExtractJsonNull() {
        JsonNode result = TreeUtils.extractJson(null);
        
        assertTrue(result.isObject());
        assertEquals(0, result.size());
    }

    @Test
    void testExtractJsonWithTrailingCommas() {
        String content = "{\"items\": [1, 2, 3,], \"data\": {\"a\": 1,}}";
        JsonNode result = TreeUtils.extractJson(content);
        
        assertTrue(result.has("items"));
        assertTrue(result.has("data"));
    }

    @Test
    void testStripJsonFence() {
        String text = "```json\n{\"result\": true}\n```";
        String result = TreeUtils.stripJsonFence(text);
        
        assertEquals("{\"result\": true}", result.strip());
    }

    @Test
    void testStripJsonFenceNoFence() {
        String text = "{\"result\": true}";
        String result = TreeUtils.stripJsonFence(text);
        
        assertEquals("{\"result\": true}", result);
    }

    @Test
    void testStripJsonFenceNull() {
        String result = TreeUtils.stripJsonFence(null);
        
        assertEquals("", result);
    }

    @Test
    void testParsePhysicalIndexWithBrackets() {
        Integer result = TreeUtils.parsePhysicalIndex("<physical_index_5>");
        
        assertEquals(5, result);
    }

    @Test
    void testParsePhysicalIndexWithoutBrackets() {
        Integer result = TreeUtils.parsePhysicalIndex("physical_index_10");
        
        assertEquals(10, result);
    }

    @Test
    void testParsePhysicalIndexInvalid() {
        Integer result = TreeUtils.parsePhysicalIndex("invalid_format");
        
        assertNull(result);
    }

    @Test
    void testParsePhysicalIndexNull() {
        Integer result = TreeUtils.parsePhysicalIndex(null);
        
        assertNull(result);
    }

    @Test
    void testParsePhysicalIndexZero() {
        Integer result = TreeUtils.parsePhysicalIndex("physical_index_0");
        
        assertEquals(0, result);
    }

    @Test
    void testExtractJsonComplexStructure() {
        String content = """
            ```json
            {
              "nodes": [
                {"id": 1, "title": "Node 1"},
                {"id": 2, "title": "Node 2"}
              ],
              "metadata": {
                "total": 2,
                "timestamp": "2026-03-05"
              }
            }
            ```""";
        JsonNode result = TreeUtils.extractJson(content);
        
        assertEquals(2, result.get("nodes").size());
        assertEquals(2, result.get("metadata").get("total").asInt());
    }
}
