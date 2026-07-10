package io.ic.starter.eval;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads relevance judgments from the WANDS label.csv (tab-separated:
 * id, query_id, chunk_id, label). Binarizes graded labels: by default
 * Exact + Partial count as relevant; strict mode counts Exact only.
 */
public class QrelsLoader {

    public enum Mode {
        EXACT_ONLY,
        EXACT_AND_PARTIAL
    }

    public Qrels load(Path labelCsv, Set<Long> queryIds, Mode mode) {
        try (BufferedReader reader = Files.newBufferedReader(labelCsv, StandardCharsets.UTF_8)) {
            Map<Long, Set<Long>> relevant = new HashMap<>();
            reader.readLine(); // header
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] f = line.split("\t", -1);
                if (f.length < 4) {
                    continue;
                }
                long queryId = Long.parseLong(f[1].trim());
                if (!queryIds.contains(queryId)) {
                    continue;
                }
                String label = f[3].trim();
                boolean isRelevant = mode == Mode.EXACT_AND_PARTIAL
                        ? (label.equals("Exact") || label.equals("Partial"))
                        : label.equals("Exact");
                if (isRelevant) {
                    long chunkId = Long.parseLong(f[2].trim());
                    relevant.computeIfAbsent(queryId, _ -> new HashSet<>()).add(chunkId);
                }
            }
            return new Qrels(relevant);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
