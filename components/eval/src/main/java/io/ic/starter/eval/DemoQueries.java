package io.ic.starter.eval;

import java.util.List;

/** The scripted queries, in presentation order, shared by the app and smoke check. */
public final class DemoQueries {
    public static final List<String> HERO_QUERIES = List.of(
            "my database keeps growing even though I delete rows",
            "wal_level logical",
            "writes are slow when many clients commit at once");

    private DemoQueries() {}
}
