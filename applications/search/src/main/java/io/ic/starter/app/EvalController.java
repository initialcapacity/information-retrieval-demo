package io.ic.starter.app;

import io.ic.starter.eval.EvalReport;
import io.javalin.http.Context;

import java.util.Map;

public class EvalController {
    private final EvalReport report;

    public EvalController(EvalReport report) {
        this.report = report;
    }

    public void index(Context ctx) {
        ctx.render("eval.ftl", Map.of("report", report, "active", "eval"));
    }
}
