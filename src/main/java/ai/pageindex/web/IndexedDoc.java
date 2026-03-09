package ai.pageindex.web;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Persists a fully-indexed document (structure + page tokens) in MongoDB. */
@Document("indexed_docs")
public class IndexedDoc {

    @Id
    private String id;

    /** Filename without extension — used as lookup key (e.g. "my_report"). */
    @Indexed(unique = true)
    private String docKey;

    private String filename;
    private String structureJson;
    private String pagesJson;
    private int    pageCount;
    private Instant indexedAt;

    public IndexedDoc() {}

    public String  getId()            { return id; }
    public String  getDocKey()        { return docKey; }
    public String  getFilename()      { return filename; }
    public String  getStructureJson() { return structureJson; }
    public String  getPagesJson()     { return pagesJson; }
    public int     getPageCount()     { return pageCount; }
    public Instant getIndexedAt()     { return indexedAt; }

    public void setId(String id)                   { this.id = id; }
    public void setDocKey(String docKey)           { this.docKey = docKey; }
    public void setFilename(String filename)       { this.filename = filename; }
    public void setStructureJson(String json)      { this.structureJson = json; }
    public void setPagesJson(String json)          { this.pagesJson = json; }
    public void setPageCount(int pageCount)        { this.pageCount = pageCount; }
    public void setIndexedAt(Instant indexedAt)    { this.indexedAt = indexedAt; }
}
