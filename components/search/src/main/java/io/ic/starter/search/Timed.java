package io.ic.starter.search;

import java.util.function.Supplier;

/**
 * A value paired with the wall-clock time its production took. Lets callers
 * report per-method retrieval latency without threading a clock through the
 * gateways.
 */
public record Timed<T>(T value, long nanos) {

    public static <T> Timed<T> of(Supplier<T> work) {
        long start = System.nanoTime();
        T value = work.get();
        return new Timed<>(value, System.nanoTime() - start);
    }

    public double millis() {
        return nanos / 1_000_000.0;
    }
}
