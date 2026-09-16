package io.ic.starter.app;

import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    @Test
    void rendersCommittedEvalSnapshotAndItsProvenance() throws Exception {
        var report = new EvalReportLoader().load();
        var output = new StringWriter();
        StarterSetup.freemarkerConfiguration().getTemplate("eval.ftl")
                .process(Map.of("report", report, "active", "eval"), output);
        assertTrue(output.toString().contains("Exact only"));
        assertTrue(output.toString().contains("Exact + Partial"));
        assertTrue(output.toString().contains("does not run a new evaluation"));
    }

}
