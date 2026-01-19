package org.example.scip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scip.Scip;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Parses SCIP protobuf index files.
 */
public class ScipParser {

    private static final Logger logger = LoggerFactory.getLogger(ScipParser.class);

    /**
     * Parse a SCIP index file.
     * @param scipFile Path to .scip file (can be gzipped)
     * @return Parsed SCIP Index
     * @throws ScipException if parsing fails
     */
    public Scip.Index parse(Path scipFile) throws ScipException {
        if (!Files.exists(scipFile)) {
            throw new ScipException("SCIP file not found: " + scipFile);
        }

        logger.info("Parsing SCIP index: {}", scipFile);
        long startTime = System.currentTimeMillis();

        try (InputStream is = openInputStream(scipFile)) {
            Scip.Index index = Scip.Index.parseFrom(is);

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("Parsed SCIP index in {}ms: {} documents, {} external symbols",
                elapsed, index.getDocumentsCount(), index.getExternalSymbolsCount());

            return index;
        } catch (IOException e) {
            throw ScipException.parseError("IO error reading file", e);
        }
    }

    /**
     * Open input stream, handling gzip compression if needed.
     */
    private InputStream openInputStream(Path file) throws IOException {
        InputStream is = new FileInputStream(file.toFile());

        // Check for gzip magic bytes
        if (file.toString().endsWith(".gz") || isGzipped(file)) {
            return new GZIPInputStream(is);
        }
        return is;
    }

    /**
     * Check if file is gzipped by looking at magic bytes.
     */
    private boolean isGzipped(Path file) {
        try (InputStream is = new FileInputStream(file.toFile())) {
            byte[] magic = new byte[2];
            if (is.read(magic) == 2) {
                return (magic[0] == (byte) 0x1f) && (magic[1] == (byte) 0x8b);
            }
        } catch (IOException e) {
            // Ignore
        }
        return false;
    }

    /**
     * Extract all documents from the index.
     */
    public List<Scip.Document> getDocuments(Scip.Index index) {
        return new ArrayList<>(index.getDocumentsList());
    }

    /**
     * Get metadata from the index.
     */
    public Scip.Metadata getMetadata(Scip.Index index) {
        return index.getMetadata();
    }

    /**
     * Count total symbols across all documents.
     */
    public int countSymbols(Scip.Index index) {
        int count = 0;
        for (Scip.Document doc : index.getDocumentsList()) {
            count += doc.getSymbolsCount();
        }
        return count;
    }

    /**
     * Count total occurrences across all documents.
     */
    public int countOccurrences(Scip.Index index) {
        int count = 0;
        for (Scip.Document doc : index.getDocumentsList()) {
            count += doc.getOccurrencesCount();
        }
        return count;
    }
}

