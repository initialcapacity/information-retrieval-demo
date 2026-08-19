package io.ic.starter.app;

import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

class FreemarkerEscapingTest {
    @Test
    void escapesUserControlledQueryAndErrorText() throws Exception {
        String payload = "\"><img src=x onerror=alert('x')>";
        var view = new SearchView(
                payload, true, false, null, "Search failed", payload,
                List.of(), null, List.of(), 0, List.of());
        var output = new StringWriter();

        StarterSetup.freemarkerConfiguration()
                .getTemplate("search.ftl")
                .process(Map.of("view", view, "active", "search"), output);

        assertFalse(output.toString().contains(payload));
    }
}
