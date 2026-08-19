package io.ic.starter.eval;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Precision / recall / F1 at a fixed cutoff k.
 */
public class Metrics {

    public static double precisionAtK(List<Long> retrieved, Set<Long> relevant, int k) {
        if (k <= 0) {
            return 0.0;
        }
        List<Long> top = retrieved.stream().limit(k).toList();
        long hits = top.stream().filter(relevant::contains).count();
        return (double) hits / k;
    }

    public static double recallAtK(List<Long> retrieved, Set<Long> relevant, int k) {
        if (relevant.isEmpty()) {
            return 0.0;
        }
        Set<Long> top = new HashSet<>(retrieved.stream().limit(k).toList());
        long hits = relevant.stream().filter(top::contains).count();
        return (double) hits / relevant.size();
    }

    public static double f1(double precision, double recall) {
        if (precision + recall == 0.0) {
            return 0.0;
        }
        return 2 * precision * recall / (precision + recall);
    }
}
