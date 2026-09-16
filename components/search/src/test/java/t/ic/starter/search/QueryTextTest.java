package t.ic.starter.search;

import io.ic.starter.search.QueryText;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryTextTest {
    @Test
    void cacheKeysDoNotDependOnTheHostLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("idle in transaction", QueryText.normalize("  IDLE   IN TRANSACTION "));
        } finally {
            Locale.setDefault(previous);
        }
    }
}
