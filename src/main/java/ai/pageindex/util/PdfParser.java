package ai.pageindex.util;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF parsing utilities.
 * Mirrors the PDF-related functions in utils.py (get_page_tokens, get_text_of_pages, etc.).
 *
 * PageEntry: a tuple of (pageText, tokenCount) per page — mirrors Python's (page_text, token_length).
 */
public class PdfParser {

    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();

    /** A single page: raw text + its token count. */
    public record PageEntry(String text, int tokenCount) {}

    /**
     * Extract all pages from a PDF file, returning text + token count per page.
     * Mirrors get_page_tokens(pdf_path, model) in utils.py.
     */
    public static List<PageEntry> getPageTokens(String pdfPath, String model) throws IOException {
        Encoding enc = getEncoding(model);
        List<PageEntry> pages = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(new File(pdfPath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            int total = doc.getNumberOfPages();
            for (int i = 1; i <= total; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(doc);
                int tokens = enc.encode(text).size();
                pages.add(new PageEntry(text, tokens));
            }
        }
        return pages;
    }

    /** Overload that uses the default model encoding. */
    public static List<PageEntry> getPageTokens(String pdfPath) throws IOException {
        return getPageTokens(pdfPath, "gpt-4o-2024-11-20");
    }

    /** Quick page count without tokenisation — used for free-tier limit checks. */
    public static int getPageCount(String pdfPath) throws IOException {
        try (PDDocument doc = Loader.loadPDF(new File(pdfPath))) {
            return doc.getNumberOfPages();
        }
    }

    /**
     * Extract text from pages [startPage, endPage] (1-based, inclusive).
     * Mirrors get_text_of_pages in utils.py.
     */
    public static String getTextOfPages(String pdfPath, int startPage, int endPage, boolean tag) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (PDDocument doc = Loader.loadPDF(new File(pdfPath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int i = startPage; i <= endPage; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(doc);
                if (tag) {
                    sb.append("<start_index_").append(i).append(">\n")
                      .append(text).append("\n")
                      .append("<end_index_").append(i).append(">\n");
                } else {
                    sb.append(text);
                }
            }
        }
        return sb.toString();
    }

    /**
     * Build page text with physical_index labels for LLM context.
     * Mirrors get_text_of_pdf_pages_with_labels in utils.py.
     */
    public static String getTextOfPdfPagesWithLabels(List<PageEntry> pages, int startPage, int endPage) {
        StringBuilder sb = new StringBuilder();
        for (int page = startPage; page <= endPage; page++) {
            int idx = page - 1;
            if (idx < 0 || idx >= pages.size()) continue;
            sb.append("<physical_index_").append(page).append(">\n")
              .append(pages.get(idx).text()).append("\n")
              .append("<physical_index_").append(page).append(">\n");
        }
        return sb.toString();
    }

    /**
     * Build plain concatenated text for pages [startPage, endPage] (1-based, inclusive).
     * Mirrors get_text_of_pdf_pages in utils.py.
     */
    public static String getTextOfPdfPages(List<PageEntry> pages, int startPage, int endPage) {
        StringBuilder sb = new StringBuilder();
        for (int page = startPage; page <= endPage; page++) {
            int idx = page - 1;
            if (idx >= 0 && idx < pages.size()) {
                sb.append(pages.get(idx).text());
            }
        }
        return sb.toString();
    }

    /**
     * Count tokens in a string using tiktoken.
     * Mirrors count_tokens(text, model) in utils.py.
     */
    public static int countTokens(String text, String model) {
        if (text == null || text.isEmpty()) return 0;
        Encoding enc = getEncoding(model);
        return enc.encode(text).size();
    }

    public static int countTokens(String text) {
        return countTokens(text, "gpt-4o-2024-11-20");
    }

    private static Encoding getEncoding(String model) {
        // JTokkit uses ModelType enum; fall back to cl100k_base for gpt-4o family
        try {
            return REGISTRY.getEncodingForModel(ModelType.GPT_4O);
        } catch (Exception e) {
            // fallback
            return REGISTRY.getEncoding(com.knuddels.jtokkit.api.EncodingType.CL100K_BASE);
        }
    }
}
