package io.ic.starter.databasesupport;

import javax.sql.DataSource;

public class HealthGateway {
    private final DatabaseTemplate databaseTemplate;

    public HealthGateway(DataSource dataSource) {
        databaseTemplate = new DatabaseTemplate(dataSource);
    }

    public boolean isDatabaseHealthy() {
        return databaseTemplate.query("select 1", rs -> rs.getInt(1)).orElse(-1) == 1;
    }
}
