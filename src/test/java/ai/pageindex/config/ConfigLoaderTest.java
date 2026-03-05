package ai.pageindex.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConfigLoader class.
 * Tests configuration loading with defaults and user overrides.
 */
class ConfigLoaderTest {

    private ConfigLoader configLoader;
    private PageIndexConfig config;

    @BeforeEach
    void setUp() {
        configLoader = new ConfigLoader();
        config = new PageIndexConfig();
    }

    @Test
    void testLoadWithDefaults() {
        PageIndexConfig loaded = configLoader.load();
        
        assertNotNull(loaded);
        assertNotNull(loaded.model);
        assertFalse(loaded.model.isEmpty());
        assertTrue(loaded.tocCheckPageNum > 0);
        assertTrue(loaded.maxPageNumEachNode > 0);
        assertTrue(loaded.maxTokenNumEachNode > 0);
    }

    @Test
    void testLoadWithUserOverrides() {
        config.model = "gpt-4";
        config.tocCheckPageNum = 15;
        config.maxPageNumEachNode = 25;
        
        PageIndexConfig loaded = configLoader.load(config);
        
        assertEquals("gpt-4", loaded.model);
        assertEquals(15, loaded.tocCheckPageNum);
        assertEquals(25, loaded.maxPageNumEachNode);
    }

    @Test
    void testLoadWithPartialOverrides() {
        config.model = "gpt-3.5";
        
        PageIndexConfig loaded = configLoader.load(config);
        
        assertEquals("gpt-3.5", loaded.model);
        assertTrue(loaded.tocCheckPageNum > 0);
        assertTrue(loaded.maxPageNumEachNode > 0);
    }

    @Test
    void testLoadWithNullUserOpt() {
        PageIndexConfig loaded = configLoader.load(null);
        
        assertNotNull(loaded);
        assertNotNull(loaded.model);
        assertTrue(loaded.tocCheckPageNum > 0);
    }

    @Test
    void testLoadPreservesDefaults() {
        config.model = null;
        
        PageIndexConfig loaded = configLoader.load(config);
        
        assertNotNull(loaded.model);
        assertFalse(loaded.model.isEmpty());
    }

    @Test
    void testLoadWithZeroValues() {
        config.tocCheckPageNum = 0;
        config.maxPageNumEachNode = 0;
        
        PageIndexConfig loaded = configLoader.load(config);
        
        // Zero values should not override defaults
        assertTrue(loaded.tocCheckPageNum > 0);
        assertTrue(loaded.maxPageNumEachNode > 0);
    }
}
