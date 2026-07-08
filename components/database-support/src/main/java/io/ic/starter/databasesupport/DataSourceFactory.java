package io.ic.starter.databasesupport;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public class DataSourceFactory {
    public static HikariDataSource create(String url, int maximumPoolSize) {
        return create(url, maximumPoolSize, null);
    }

    public static HikariDataSource create(String url, int maximumPoolSize, String connectionInitSql) {
        if (url == null) {
            throw new IllegalArgumentException("data source url cannot be null");
        }
        var config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setMaximumPoolSize(maximumPoolSize);
        if (connectionInitSql != null) {
            config.setConnectionInitSql(connectionInitSql);
        }

        return new HikariDataSource(config);
    }

    public static DataSource create(String url) {
        return create(url, 10);
    }
}
