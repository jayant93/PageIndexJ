package ai.pageindex.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * JSON-based logger that writes all log entries to a single JSON file.
 * Mirrors Python's JsonLogger class in utils.py.
 */
public class JsonLogger {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ArrayNode logData = mapper.createArrayNode();
    private final String filename;

    public JsonLogger(String docPath) {
        String docName = new File(docPath).getName();
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        this.filename = docName + "_" + timestamp + ".json";
        new File("logs").mkdirs();
    }

    public void log(Object message) {
        if (message instanceof ObjectNode) {
            logData.add((ObjectNode) message);
        } else {
            ObjectNode entry = mapper.createObjectNode();
            entry.put("message", message == null ? "null" : message.toString());
            logData.add(entry);
        }
        flush();
    }

    public void info(Object message) {
        log(message);
    }

    public void error(Object message) {
        log(message);
    }

    public void debug(Object message) {
        log(message);
    }

    private void flush() {
        try {
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File("logs/" + filename), logData);
        } catch (IOException e) {
            System.err.println("JsonLogger flush error: " + e.getMessage());
        }
    }
}
