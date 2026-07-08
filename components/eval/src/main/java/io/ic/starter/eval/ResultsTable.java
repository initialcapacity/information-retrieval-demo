package io.ic.starter.eval;

import java.util.List;

/**
 * Formats a comparison table of method metrics for console output.
 */
public class ResultsTable {

    public static String format(String title, int k, List<MethodMetrics> rows) {
        var sb = new StringBuilder();
        sb.append(title).append("  (k=").append(k).append(")\n");
        sb.append(String.format("%-10s %8s %12s %9s %7s%n", "method", "queries", "precision", "recall", "f1"));
        sb.append("-".repeat(50)).append("\n");
        for (MethodMetrics row : rows) {
            sb.append(String.format("%-10s %8d %12.4f %9.4f %7.4f%n",
                    row.method(), row.queryCount(), row.precision(), row.recall(), row.f1()));
        }
        return sb.toString();
    }
}
