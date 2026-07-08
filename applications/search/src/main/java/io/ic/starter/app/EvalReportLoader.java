package io.ic.starter.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ic.starter.eval.EvalReport;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.IOException;

/**
 * Loads the committed eval snapshot (real numbers) from the classpath so the
 * eval view renders instantly and offline.
 */
public class EvalReportLoader {
    private static final String RESOURCE = "/eval-results.json";

    public EvalReport load() {
        try (InputStream stream = EvalReportLoader.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing " + RESOURCE + "; run :applications:tools:runEval to generate it");
            }
            return new ObjectMapper().readValue(stream, EvalReport.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
