package t.ic.starter.search;

import io.ic.starter.search.Timed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TimedTest {

    @Test
    void capturesTheValueAndTheElapsedTime() {
        var timed = Timed.of(() -> {
            sleep(15);
            return "results";
        });

        assertEquals("results", timed.value());
        assertTrue(timed.millis() >= 10, "expected at least 10 ms, got " + timed.millis());
    }

    @Test
    void propagatesFailures() {
        assertThrows(IllegalStateException.class, () -> Timed.of(() -> {
            throw new IllegalStateException("no embedding available");
        }));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
