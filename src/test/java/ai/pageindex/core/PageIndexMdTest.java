package ai.pageindex.core;

import ai.pageindex.util.OpenAIClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PageIndexMd class.
 * Tests markdown parsing and node extraction.
 */
class PageIndexMdTest {

    @Mock
    private OpenAIClient mockAiClient;
    
    private PageIndexMd pageIndexMd;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        pageIndexMd = new PageIndexMd(mockAiClient);
    }

    @Test
    void testExtractNodesFromMarkdownEmpty() {
        PageIndexMd.ExtractionResult result = pageIndexMd.extractNodesFromMarkdown("");
        
        assertNotNull(result);
        assertNotNull(result.lines());
        assertNotNull(result.nodeList());
    }

    @Test
    void testExtractNodesFromMarkdownNoHeaders() {
        String markdown = "This is plain text\nwith multiple lines\nbut no headers";
        PageIndexMd.ExtractionResult result = pageIndexMd.extractNodesFromMarkdown(markdown);
        
        assertNotNull(result);
        assertEquals(3, result.lines().size());
    }

    @Test
    void testExtractionResultRecord() {
        List<Map<String, Object>> nodeList = List.of();
        List<String> lines = List.of("line1", "line2");
        
        PageIndexMd.ExtractionResult result = 
            new PageIndexMd.ExtractionResult(nodeList, lines);
        
        assertEquals(nodeList, result.nodeList());
        assertEquals(lines, result.lines());
    }

    @Test
    void testExtractionResultEquality() {
        List<Map<String, Object>> nodeList = List.of();
        List<String> lines = List.of("line1");
        
        PageIndexMd.ExtractionResult result1 = 
            new PageIndexMd.ExtractionResult(nodeList, lines);
        PageIndexMd.ExtractionResult result2 = 
            new PageIndexMd.ExtractionResult(nodeList, lines);
        
        assertEquals(result1, result2);
    }

    @Test
    void testConstructorInitialization() {
        assertNotNull(pageIndexMd);
    }

    @Test
    void testExtractNodesFromMarkdownWithCodeBlocks() {
        String markdown = """
            # Header 1
            
            ```
            code block content
            ```
            
            ## Header 2""";
        
        PageIndexMd.ExtractionResult result = pageIndexMd.extractNodesFromMarkdown(markdown);
        
        assertNotNull(result);
        assertTrue(result.lines().size() > 0);
        assertNotNull(result.nodeList());
    }

    @Test
    void testExtractNodesFromMarkdownMultipleHeaders() {
        String markdown = """
            # H1
            ## H2
            ### H3
            #### H4
            ##### H5
            ###### H6""";
        
        PageIndexMd.ExtractionResult result = pageIndexMd.extractNodesFromMarkdown(markdown);
        
        assertNotNull(result);
        assertEquals(6, result.lines().size());
    }

    @Test
    void testExtractNodesPreservesContent() {
        String markdown = "# Header\nSome content\nMore content";
        
        PageIndexMd.ExtractionResult result = pageIndexMd.extractNodesFromMarkdown(markdown);
        
        assertTrue(result.lines().contains("# Header"));
        assertTrue(result.lines().contains("Some content"));
    }
}
