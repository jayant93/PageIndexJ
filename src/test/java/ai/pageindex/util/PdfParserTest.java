package ai.pageindex.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PdfParser class.
 * Tests PDF text and token extraction.
 */
class PdfParserTest {

    @Test
    void testPageEntryRecord() {
        PdfParser.PageEntry entry = new PdfParser.PageEntry("Sample text", 10);
        
        assertEquals("Sample text", entry.text());
        assertEquals(10, entry.tokenCount());
    }

    @Test
    void testPageEntryEquality() {
        PdfParser.PageEntry entry1 = new PdfParser.PageEntry("Text", 5);
        PdfParser.PageEntry entry2 = new PdfParser.PageEntry("Text", 5);
        
        assertEquals(entry1, entry2);
    }

    @Test
    void testPageEntryInequality() {
        PdfParser.PageEntry entry1 = new PdfParser.PageEntry("Text1", 5);
        PdfParser.PageEntry entry2 = new PdfParser.PageEntry("Text2", 5);
        
        assertNotEquals(entry1, entry2);
    }

    @Test
    void testPageEntryNullText() {
        PdfParser.PageEntry entry = new PdfParser.PageEntry(null, 0);
        
        assertNull(entry.text());
        assertEquals(0, entry.tokenCount());
    }

    @Test
    void testPageEntryEmptyText() {
        PdfParser.PageEntry entry = new PdfParser.PageEntry("", 0);
        
        assertEquals("", entry.text());
        assertEquals(0, entry.tokenCount());
    }

    @Test
    void testPageEntryLargeTokenCount() {
        PdfParser.PageEntry entry = new PdfParser.PageEntry("Text", 100000);
        
        assertEquals(100000, entry.tokenCount());
    }

    @Test
    void testGetPageTokensWithInvalidPath() {
        assertThrows(IOException.class, () -> {
            PdfParser.getPageTokens("/nonexistent/file.pdf");
        });
    }

    @Test
    void testGetPageTokensWithValidModel() {
        assertThrows(IOException.class, () -> {
            PdfParser.getPageTokens("/nonexistent/file.pdf", "gpt-4o-2024-11-20");
        });
    }

    @Test
    void testGetTextOfPagesThrowsOnInvalidPath() {
        assertThrows(IOException.class, () -> {
            PdfParser.getTextOfPages("/nonexistent/file.pdf", 1, 1, false);
        });
    }

    @Test
    void testPageEntryWithSpecialCharacters() {
        String textWithSpecialChars = "Text with äöü and 你好 characters";
        PdfParser.PageEntry entry = new PdfParser.PageEntry(textWithSpecialChars, 20);
        
        assertEquals(textWithSpecialChars, entry.text());
        assertEquals(20, entry.tokenCount());
    }

    @Test
    void testDefaultModel() {
        // Verify the default model can be handled
        assertDoesNotThrow(() -> {
            // Will fail due to missing PDF, but should handle the model parameter
            try {
                PdfParser.getPageTokens("/nonexistent/file.pdf");
            } catch (IOException e) {
                // Expected - file doesn't exist
                assertTrue(e.getMessage().contains("nonexistent") || 
                          e.getMessage().contains("No such file") ||
                          e.getMessage().contains("cannot find"));
            }
        });
    }
}
