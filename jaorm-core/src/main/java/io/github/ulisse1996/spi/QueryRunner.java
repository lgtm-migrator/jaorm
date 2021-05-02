package io.github.ulisse1996.spi;

import io.github.ulisse1996.ServiceFinder;
import io.github.ulisse1996.ResultSetExecutor;
import io.github.ulisse1996.UpdateExecutor;
import io.github.ulisse1996.entity.sql.DataSourceProvider;
import io.github.ulisse1996.entity.sql.SqlAccessor;
import io.github.ulisse1996.entity.sql.SqlParameter;
import io.github.ulisse1996.exception.JaormSqlException;
import io.github.ulisse1996.logger.JaormLogger;
import io.github.ulisse1996.logger.SqlJaormLogger;
import io.github.ulisse1996.mapping.TableRow;
import io.github.ulisse1996.spi.common.Singleton;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Stream;

public abstract class QueryRunner {

    protected static final SqlJaormLogger logger = JaormLogger.getSqlLogger(ResultSetExecutor.class);
    private static final Singleton<QueryRunner> ENTITY_RUNNER = Singleton.instance();
    private static final Singleton<QueryRunner> SIMPLE_RUNNER = Singleton.instance();

    public static QueryRunner getInstance(Class<?> klass) {
        if (!isDelegate(klass)) {
            return getSimple();
        }

        if (!ENTITY_RUNNER.isPresent()) {
            for (QueryRunner runner : ServiceFinder.loadServices(QueryRunner.class)) {
                if (runner.isCompatible(klass)) {
                    ENTITY_RUNNER.set(runner);
                }
            }

            if (ENTITY_RUNNER.isPresent()) {
                return ENTITY_RUNNER.get();
            }
        } else {
            return ENTITY_RUNNER.get();
        }

        throw new IllegalArgumentException("Can't find a matched runner for klass " + klass);
    }

    private static boolean isDelegate(Class<?> klass) {
        return DelegatesService.getInstance().getDelegates().containsKey(klass);
    }

    public static QueryRunner getSimple() {
        if (!SIMPLE_RUNNER.isPresent()) {
            for (QueryRunner runner : ServiceFinder.loadServices(QueryRunner.class)) {
                if (runner.isSimple()) {
                    SIMPLE_RUNNER.set(runner);
                }
            }

            if (SIMPLE_RUNNER.isPresent()) {
                return SIMPLE_RUNNER.get();
            }
        } else {
            return SIMPLE_RUNNER.get();
        }

        throw new IllegalArgumentException("Can't find Simple Runner");
    }

    protected Map<String, Object> doUpdate(String query, List<SqlParameter> params, Map<String, Class<?>> autoGenerated) {
        logger.logSql(query, params);
        try (Connection connection = getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(query,
                     autoGenerated.keySet().toArray(new String[0]));
             UpdateExecutor executor = new UpdateExecutor(preparedStatement, params)) {
            if (executor.getResultSet() != null && executor.getResultSet().next()) {
                Map<String, Object> generated = new HashMap<>();
                for (Map.Entry<String, Class<?>> entry : autoGenerated.entrySet()) {
                    Object value = SqlAccessor.find(entry.getValue())
                            .getGetter().get(executor.getResultSet(), entry.getKey());
                    generated.put(entry.getKey(), value);
                }
                return generated;
            } else {
                return Collections.emptyMap();
            }
        } catch (SQLException ex) {
            logger.error("Error during update/insert/delete"::toString, ex);
            throw new JaormSqlException(ex);
        }
    }

    protected Connection getConnection() throws SQLException {
        DataSourceProvider provider = DataSourceProvider.getCurrent();
        TransactionManager manager = TransactionManager.getInstance();
        if (manager instanceof TransactionManager.NoOpTransactionManager || manager.getCurrentTransaction() == null) {
            return provider.getConnection();
        } else {
            DataSourceProvider delegate = DataSourceProvider.getCurrentDelegate();
            if (delegate == null) {
                DataSourceProvider.setDelegate(manager.createDelegate(provider));
                return DataSourceProvider.getCurrentDelegate().getConnection();
            } else {
                return delegate.getConnection();
            }
        }
    }

    public abstract boolean isCompatible(Class<?> klass);
    public abstract boolean isSimple();

    public abstract <R> R read(Class<R> klass, String query, List<SqlParameter> params);
    public abstract <R> Optional<R> readOpt(Class<R> klass, String query, List<SqlParameter> params);
    public abstract <R> List<R> readAll(Class<R> klass, String query, List<SqlParameter> params);
    public abstract <R> Stream<R> readStream(Class<R> klass, String query, List<SqlParameter> params);

    public abstract TableRow read(String query, List<SqlParameter> params);
    public abstract Optional<TableRow> readOpt(String query, List<SqlParameter> params);
    public abstract Stream<TableRow> readStream(String query, List<SqlParameter> params);

    public abstract <R> R insert(R entity, String query, List<SqlParameter> params);
    public abstract void update(String query, List<SqlParameter> params);
    public abstract void delete(String query, List<SqlParameter> params);
}