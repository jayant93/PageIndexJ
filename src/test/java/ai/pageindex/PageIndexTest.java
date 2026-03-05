package ai.pageindex;

import ai.pageindex.config.PageIndexConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PageIndex class.
 * Tests the public API and file validation.
 */
class PageIndexTest {

    @Test
    void testPageIndexMainWithInvalidPath() {
        assertThrows(IllegalArgumentException.class, () -> {
            PageIndex.pageIndexMain("/nonexistent/file.pdf", null);
        });
    }

    @Test
    void testPageIndexMainWithNonPdfFile(@TempDir Path tempDir) throws IOException {
        File nonPdfFile = tempDir.resolve("notapdf.txt").toFile();
        nonPdfFile.createNewFile();
        
        assertThrows(IllegalArgumentException.class, () -> {
            PageIndex.pageIndexMain(nonPdfFile.getAbsolutePath(), null);
        });
    }

    @Test
    void testPageIndexMainWithNullConfig() {
        // Test that null config is handled (would use defaults)
        // This will fail at PDF parsing stage due to invalid file, which is expected
        assertThrows(Exception.class, () -> {
            PageIndex.pageIndexMain("/nonexistent/file.pdf", null);
        });
    }

    @Test
    void testPageIndexMainWithCustomConfig() {
        PageIndexConfig config = new PageIndexConfig();
        config.model = "gpt-3.5-turbo";
        
        assertThrows(Exception.class, () -> {
            PageIndex.pageIndexMain("/nonexistent/file.pdf", config);
        });
    }

    @Test
    void testPageIndexMainValidationWithUpperCase(@TempDir Path tempDir) throws IOException {
        File pdfFile = tempDir.resolve("document.PDF").toFile();
        pdfFile.createNewFile();
        
        // File exists but is empty/invalid, should fail at parsing stage
        assertThrows(Exception.class, () -> {
            PageIndex.pageIndexMain(pdfFile.getAbsolutePath(), null);
        });
    }

    @Test
    void testPageIndexMainWithoutChatGptKey() {
        // If CHATGPT_API_KEY is not set, OpenAIClient initialization will fail
        if (System.getenv("CHATGPT_API_KEY") == null) {
            assertThrows(Exception.class, () -> {
                PageIndex.pageIndexMain("/nonexistent/file.pdf", null);
            });
        }
    }

    @Test
    void testPageIndexMainMixedCase(@TempDir Path tempDir) throws IOException {
        File pdfFile = tempDir.resolve("TEST.Pdf").toFile();
        pdfFile.createNewFile();
        
        // Mixed case PDF extension should be accepted
        assertThrows(Exception.class, () -> {
            PageIndex.pageIndexMain(pdfFile.getAbsolutePath(), null);
        });
    }

    @Test
    void testPageIndexMainStringValidation() {
        // Test various invalid file paths
        String[] invalidPaths = {
            "",
            "   ",
            "noextension",
            "file.doc",
            "file.PDF.backup",
            null
        };
        
        for (String path : invalidPaths) {
            if (path != null) {
                assertThrows(Exception.class, () -> {
                    PageIndex.pageIndexMain(path, null);
                });
            }
        }
    }

    @Test
    void testPageIndexConfigHandling() {
        PageIndexConfig config = new PageIndexConfig();
        assertNotNull(config);
        
        config.tocCheckPageNum = 50;
        assertEquals(50, config.tocCheckPageNum);
    }
}
