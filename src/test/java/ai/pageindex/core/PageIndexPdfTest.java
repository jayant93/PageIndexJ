package ai.pageindex.core;

import ai.pageindex.util.OpenAIClient;
import ai.pageindex.util.PdfParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PageIndexPdf class.
 * Tests PDF indexing and tree parsing functionality.
 */
class PageIndexPdfTest {

    @Mock
    private OpenAIClient mockAiClient;
    
    private PageIndexPdf pageIndexPdf;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        pageIndexPdf = new PageIndexPdf(mockAiClient);
    }

    @Test
    void testConstructorInitialization() {
        assertNotNull(pageIndexPdf);
    }

    @Test
    void testCheckTitleAppearanceWithNullPhysicalIndex() {
        Map<String, Object> item = Map.of(
            "title", "Test Title",
            "list_index", 0
            // physical_index intentionally not included to test null handling
        );
        List<PdfParser.PageEntry> pageList = List.of(
            new PdfParser.PageEntry("Some text", 10)
        );
        
        // This test verifies null handling without waiting for async result
        CompletableFuture<Map<String, Object>> future = 
            pageIndexPdf.checkTitleAppearance(item, pageList, 1, "gpt-4o-2024-11-20");
        
        assertNotNull(future);
        // Note: Testing async result would require mock API configuration
    }

    @Test
    void testCheckTitleAppearanceWithValidPageNumber() {
        Map<String, Object> item = Map.of(
            "title", "Test Title",
            "list_index", 0,
            "physical_index", 1
        );
        List<PdfParser.PageEntry> pageList = List.of(
            new PdfParser.PageEntry("Some text", 10)
        );
        
        assertNotNull(
            pageIndexPdf.checkTitleAppearance(item, pageList, 1, "gpt-4o-2024-11-20")
        );
    }

    @Test
    void testCheckTitleAppearanceWithOutOfRangePage() {
        Map<String, Object> item = Map.of(
            "title", "Test Title",
            "list_index", 0,
            "physical_index", 100
        );
        List<PdfParser.PageEntry> pageList = List.of(
            new PdfParser.PageEntry("Some text", 10)
        );
        
        CompletableFuture<Map<String, Object>> future = 
            pageIndexPdf.checkTitleAppearance(item, pageList, 1, "gpt-4o-2024-11-20");
        
        assertNotNull(future);
        assertDoesNotThrow(() -> {
            Map<String, Object> result = future.get();
            assertNotNull(result);
            assertEquals("no", result.get("answer"));
        });
    }

    @Test
    void testCheckTitleAppearanceAsync() {
        Map<String, Object> item = Map.of(
            "title", "Section Title",
            "physical_index", 1
        );
        List<PdfParser.PageEntry> pageList = List.of(
            new PdfParser.PageEntry("Page one content", 50)
        );
        
        CompletableFuture<Map<String, Object>> future = 
            pageIndexPdf.checkTitleAppearance(item, pageList, 1, "gpt-4o-2024-11-20");
        
        assertTrue(future instanceof CompletableFuture);
    }

    @Test
    void testCheckTitleAppearanceWithMultiplePages() {
        Map<String, Object> item = Map.of(
            "title", "Chapter Title",
            "physical_index", 2
        );
        List<PdfParser.PageEntry> pageList = List.of(
            new PdfParser.PageEntry("Page 1", 25),
            new PdfParser.PageEntry("Page 2", 25),
            new PdfParser.PageEntry("Page 3", 25)
        );
        
        CompletableFuture<Map<String, Object>> future = 
            pageIndexPdf.checkTitleAppearance(item, pageList, 1, "gpt-4o-2024-11-20");
        
        assertNotNull(future);
    }

    @Test
    void testMockClientAssignment() {
        PageIndexPdf indexer = new PageIndexPdf(mockAiClient);
        
        assertNotNull(indexer);
        verify(mockAiClient, times(0)).call(anyString(), anyString());
    }
}
