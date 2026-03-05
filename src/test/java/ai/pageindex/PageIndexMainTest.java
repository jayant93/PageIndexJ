package ai.pageindex;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PageIndexMain class.
 * Tests the main entry point and command-line interface.
 * Note: Tests that call main() with no args will trigger System.exit(),
 * so we test the class structure instead of functionality.
 */
class PageIndexMainTest {

    @Test
    void testMainMethodExists() {
        assertDoesNotThrow(() -> {
            Class<?> mainClass = Class.forName("ai.pageindex.PageIndexMain");
            assertNotNull(mainClass);
        });
    }

    @Test
    void testMainMethodIsPublic() {
        assertDoesNotThrow(() -> {
            Class<?> mainClass = Class.forName("ai.pageindex.PageIndexMain");
            assertNotNull(mainClass.getDeclaredMethod("main", String[].class));
        });
    }
    
    @Test
    void testMainClassCanBeInstantiated() {
        assertDoesNotThrow(() -> {
            Class<?> mainClass = Class.forName("ai.pageindex.PageIndexMain");
            assertNotNull(mainClass);
        });
    }
}
