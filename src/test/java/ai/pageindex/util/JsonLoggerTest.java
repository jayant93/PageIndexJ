package ai.pageindex.util;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JsonLogger class.
 * Tests JSON logging functionality and file output.
 */
class JsonLoggerTest {

    private JsonLogger logger;
    private String docPath;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        docPath = tempDir.resolve("test_document.pdf").toString();
        logger = new JsonLogger(docPath);
    }

    @Test
    void testLogStringMessage() {
        assertDoesNotThrow(() -> {
            logger.log("Test message");
        });
    }

    @Test
    void testLogObjectMessage() {
        ObjectNode node = TreeUtils.MAPPER.createObjectNode();
        node.put("event", "test");
        
        assertDoesNotThrow(() -> {
            logger.log(node);
        });
    }

    @Test
    void testInfoMethod() {
        assertDoesNotThrow(() -> {
            logger.info("Info message");
        });
    }

    @Test
    void testErrorMethod() {
        assertDoesNotThrow(() -> {
            logger.error("Error message");
        });
    }

    @Test
    void testDebugMethod() {
        assertDoesNotThrow(() -> {
            logger.debug("Debug message");
        });
    }

    @Test
    void testLogMultipleMessages() {
        assertDoesNotThrow(() -> {
            logger.log("Message 1");
            logger.log("Message 2");
            logger.log("Message 3");
        });
    }

    @Test
    void testLogNullMessage() {
        assertDoesNotThrow(() -> {
            logger.log(null);
        });
    }

    @Test
    void testFileCreated() {
        logger.log("Test");
        
        File logsDir = new File("logs");
        assertTrue(logsDir.exists(), "logs directory should be created");
        assertTrue(logsDir.isDirectory(), "logs should be a directory");
    }

    @Test
    void testFilenameFormat() {
        // Filename should be test_document.pdf_YYYYMMdd_HHmmss.json
        logger.log("Message");
        
        File logsDir = new File("logs");
        File[] files = logsDir.listFiles();
        
        assertNotNull(files, "logs directory should contain files");
        assertTrue(files.length > 0, "at least one log file should exist");
        assertTrue(files[0].getName().startsWith("test_document.pdf_"), 
                   "filename should start with document name");
        assertTrue(files[0].getName().endsWith(".json"),
                   "filename should end with .json");
    }
}
