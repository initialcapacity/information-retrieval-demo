package io.ic.starter.databasesupport;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class DatabaseTemplate {
    private final DataSource dataSource;

    public DatabaseTemplate(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public int execute(String sql, ParameterSetter parameters) {
        return with(sql, parameters, statement -> {
            statement.execute();
            return statement.getUpdateCount();
        }, null);
    }

    public int execute(String sql) {
        return execute(sql, _ -> {
        });
    }

    public int execute(String sql, ParameterSetter parameters, Connection connection) {
        return with(sql, parameters, statement -> {
            statement.execute();
            return statement.getUpdateCount();
        }, connection);
    }

    public int execute(String sql, Connection connection) {
        return execute(sql, _ -> {
        }, connection);
    }

    public <T> Optional<T> query(String sql, ParameterSetter parameters, ResultMapper<T> mapper) {
        return with(sql, parameters, statement -> {
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(mapper.accept(results)) : Optional.empty();
            }
        }, null);
    }

    public <T> Optional<T> query(String sql, ResultMapper<T> mapper) {
        return query(sql, _ -> {
        }, mapper);
    }

    public <T> Optional<T> query(String sql, ParameterSetter parameters, ResultMapper<T> mapper, Connection connection) {
        return with(sql, parameters, statement -> {
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(mapper.accept(results)) : Optional.empty();
            }
        }, connection);
    }

    public <T> Optional<T> query(String sql, ResultMapper<T> mapper, Connection connection) {
        return query(sql, _ -> {
        }, mapper, connection);
    }

    public <T> List<T> queryList(String sql, ParameterSetter parameters, ResultMapper<T> mapper) {
        return with(sql, parameters, statement -> {
            try (ResultSet results = statement.executeQuery()) {
                var resultList = new ArrayList<T>();
                while (results.next()) {
                    resultList.add(mapper.accept(results));
                }
                return resultList;
            }
        }, null);
    }

    public <T> List<T> queryList(String sql, ResultMapper<T> mapper) {
        return queryList(sql, _ -> {
        }, mapper);
    }

    public <T> List<T> queryList(String sql, ParameterSetter parameters, ResultMapper<T> mapper, Connection connection) {
        return with(sql, parameters, statement -> {
            try (ResultSet results = statement.executeQuery()) {
                var resultList = new ArrayList<T>();
                while (results.next()) {
                    resultList.add(mapper.accept(results));
                }
                return resultList;
            }
        }, connection);
    }

    public <T> List<T> queryList(String sql, ResultMapper<T> mapper, Connection connection) {
        return queryList(sql, _ -> {
        }, mapper, connection);
    }

    public <T> T inTransaction(Function<Connection, T> actions) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                var result = actions.apply(connection);
                connection.commit();
                return result;
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T with(String sql, ParameterSetter parameters, StatementMapper<T> mapper, Connection connection) {
        if (connection == null) {
            try (Connection freshConnection = dataSource.getConnection()) {
                return prepare(sql, parameters, mapper, freshConnection);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        } else {
            return prepare(sql, parameters, mapper, connection);
        }
    }

    private static <T> T prepare(String sql, ParameterSetter parameters, StatementMapper<T> mapper, Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            parameters.accept(statement);
            return mapper.accept(statement);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
