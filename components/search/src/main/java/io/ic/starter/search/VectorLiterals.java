package io.ic.starter.search;

/**
 * Converts float arrays to the pgvector text literal form "[a,b,c]".
 */
public class VectorLiterals {
    public static String toLiteral(float[] vector) {
        var builder = new StringBuilder(vector.length * 12);
        builder.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        builder.append(']');
        return builder.toString();
    }
}
