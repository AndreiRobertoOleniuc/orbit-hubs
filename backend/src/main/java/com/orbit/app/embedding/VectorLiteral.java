package com.orbit.app.embedding;

/** pgvector text literals, for native queries that write a vector column. */
public final class VectorLiteral {

    private VectorLiteral() {
    }

    /** {@code [v1,v2,…]}; {@link Float#toString} is locale-independent and lossless. */
    public static String of(float[] vector) {
        StringBuilder literal = new StringBuilder(vector.length * 12).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) literal.append(',');
            literal.append(Float.toString(vector[i]));
        }
        return literal.append(']').toString();
    }
}
