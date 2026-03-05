package ai.pageindex.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PageIndexConfig class.
 * Tests configuration object creation and copying.
 */
class PageIndexConfigTest {

    @Test
    void testDefaultConstructor() {
        PageIndexConfig config = new PageIndexConfig();
        
        assertNotNull(config.model);
        assertEquals("gpt-4o-2024-11-20", config.model);
        assertEquals(20, config.tocCheckPageNum);
        assertEquals(10, config.maxPageNumEachNode);
        assertEquals(20000, config.maxTokenNumEachNode);
        assertEquals("yes", config.ifAddNodeId);
        assertEquals("yes", config.ifAddNodeSummary);
        assertEquals("no", config.ifAddDocDescription);
        assertEquals("no", config.ifAddNodeText);
    }

    @Test
    void testCopyConstructor() {
        PageIndexConfig original = new PageIndexConfig();
        original.model = "gpt-3.5-turbo";
        original.tocCheckPageNum = 30;
        
        PageIndexConfig copy = new PageIndexConfig(original);
        
        assertEquals(original.model, copy.model);
        assertEquals(original.tocCheckPageNum, copy.tocCheckPageNum);
        assertEquals(original.maxPageNumEachNode, copy.maxPageNumEachNode);
    }

    @Test
    void testCopyConstructorIndependence() {
        PageIndexConfig original = new PageIndexConfig();
        PageIndexConfig copy = new PageIndexConfig(original);
        
        copy.model = "new-model";
        copy.tocCheckPageNum = 99;
        
        assertNotEquals(original.model, copy.model);
        assertNotEquals(original.tocCheckPageNum, copy.tocCheckPageNum);
    }

    @Test
    void testConfigFieldModification() {
        PageIndexConfig config = new PageIndexConfig();
        
        config.model = "custom-model";
        config.maxPageNumEachNode = 50;
        config.ifAddNodeSummary = "no";
        
        assertEquals("custom-model", config.model);
        assertEquals(50, config.maxPageNumEachNode);
        assertEquals("no", config.ifAddNodeSummary);
    }

    @Test
    void testAllConfigFields() {
        PageIndexConfig config = new PageIndexConfig();
        
        assertTrue(config.model.matches("^[a-zA-Z0-9._-]+$"));
        assertTrue(config.tocCheckPageNum > 0);
        assertTrue(config.maxPageNumEachNode > 0);
        assertTrue(config.maxTokenNumEachNode > 0);
        assertNotNull(config.ifAddNodeId);
        assertNotNull(config.ifAddNodeSummary);
        assertNotNull(config.ifAddDocDescription);
        assertNotNull(config.ifAddNodeText);
    }
}
