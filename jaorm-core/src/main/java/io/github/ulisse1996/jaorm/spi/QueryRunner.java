package io.github.ulisse1996.jaorm.spi;

import io.github.ulisse1996.jaorm.ResultSetExecutor;
import io.github.ulisse1996.jaorm.ServiceFinder;
import io.github.ulisse1996.jaorm.UpdateExecutor;
import io.github.ulisse1996.jaorm.annotation.Table;
import io.github.ulisse1996.jaorm.entity.EntityDelegate;
import io.github.ulisse1996.jaorm.entity.Result;
import io.github.ulisse1996.jaorm.entity.UpdateBatchExecutor;
import io.github.ulisse1996.jaorm.entity.sql.DataSourceProvider;
import io.github.ulisse1996.jaorm.entity.sql.SqlAccessor;
import io.github.ulisse1996.jaorm.entity.sql.SqlParameter;
import io.github.ulisse1996.jaorm.exception.JaormSqlException;
import io.github.ulisse1996.jaorm.logger.JaormLogger;
import io.github.ulisse1996.jaorm.logger.SqlJaormLogger;
import io.github.ulisse1996.jaorm.mapping.TableRow;
import io.github.ulisse1996.jaorm.schema.TableInfo;
import io.github.ulisse1996.jaorm.spi.common.Singleton;
import io.github.ulisse1996.jaorm.util.ClassChecker;
import io.github.ulisse1996.jaorm.vendor.VendorSpecific;
import io.github.ulisse1996.jaorm.vendor.specific.GeneratedKeysSpecific;

import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

public abstract class QueryRunner {

    public static final SqlJaormLogger logger = JaormLogger.getSqlLogger(ResultSetExecutor.class);
    private static final Singleton<QueryRunner> ENTITY_RUNNER = Singleton.instance();
    private static final Singleton<QueryRunner> SIMPLE_RUNNER = Singleton.instance();
    private static final ThreadLocal<Map<Object, Integer>> UPDATED_ROWS_LOCAL = ThreadLocal.withInitial(HashMap::new); //NOSONAR

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

    protected static boolean isDelegate(Class<?> klass) {
        return DelegatesService.getInstance().getDelegates().entrySet().stream().anyMatch(el -> ClassChecker.isAssignable(el.getKey(), klass)) ||
                EntityDelegate.class.isAssignableFrom(klass) ||
                ProjectionsService.getInstance().getProjections().entrySet().stream().anyMatch(el -> ClassChecker.isAssignable(el.getKey(), klass));
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

    protected int doSimpleUpdate(String query, List<SqlParameter> params) {
        logger.logSql(query, params);
        try (Connection connection = getConnection(TableInfo.EMPTY);
             PreparedStatement preparedStatement = connection.prepareStatement(query);
             UpdateExecutor executor = new UpdateExecutor(preparedStatement, params, null)) {
            return executor.getUpdateRow();
        } catch (SQLException ex) {
            logger.error("Error during update/insert/delete"::toString, ex);
            throw new JaormSqlException(ex);
        }
    }

    protected List<Map<String, Object>> doBatchUpdate(Class<?> entity, String query, List<List<SqlParameter>> params, Map<String, Class<?>> autoGenerated) {
        if (!autoGenerated.isEmpty()) {
            logger.logSqlBatch(query, params);
        }
        List<Map<String, Object>> generated = new ArrayList<>();
        try (Connection connection = getConnection(DelegatesService.getInstance().getTableInfo(entity));
            PreparedStatement preparedStatement = !autoGenerated.keySet().isEmpty()
                    ? connection.prepareStatement(appendReturningKeys(query, autoGenerated.keySet(), params, true),
                        autoGenerated.keySet().toArray(new String[0]))
                    : connection.prepareStatement(query);
            UpdateBatchExecutor executor = new UpdateBatchExecutor(preparedStatement, params, autoGenerated.keySet())) {
                if (executor.getResultSet() != null) {
                    while (executor.getResultSet().next()) {
                        Map<String, Object> values = new HashMap<>();
                        for (Map.Entry<String, Class<?>> entry : autoGenerated.entrySet()) {
                            Object value = getGeneratedKey(executor.getResultSet(), entry);
                            values.put(entry.getKey(), value);
                        }
                        generated.add(values);
                    }
                    return generated;
                } else {
                    return new ArrayList<>();
                }
        } catch (SQLException ex) {
            logger.error("Error during update/insert/delete batch"::toString, ex);
            throw new JaormSqlException(ex);
        }
    }

    protected Map<String, Object> doUpdate(Class<?> entity, String query, List<SqlParameter> params, Map<String, Class<?>> autoGenerated) {
        if (autoGenerated.isEmpty()) {
            logger.logSql(query, params);
        }
        Map<String, Object> generated = new HashMap<>();
        try (Connection connection = getConnection(DelegatesService.getInstance().getTableInfo(entity));
             PreparedStatement preparedStatement = !autoGenerated.keySet().isEmpty()
                     ? connection.prepareStatement(appendReturningKeys(query, autoGenerated.keySet(), params, false),
                        autoGenerated.keySet().toArray(new String[0]))
                     : connection.prepareStatement(query);
             UpdateExecutor executor = new UpdateExecutor(preparedStatement, params, autoGenerated.keySet())) {
            if (executor.getResultSet() != null && executor.getResultSet().next()) {
                for (Map.Entry<String, Class<?>> entry : autoGenerated.entrySet()) {
                    Object value = getGeneratedKey(executor.getResultSet(), entry);
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

    @SuppressWarnings("unchecked")
    private String appendReturningKeys(String query, Set<String> keys, List<?> params, boolean batch) {
        GeneratedKeysSpecific specific = VendorSpecific.getSpecific(GeneratedKeysSpecific.class, GeneratedKeysSpecific.NO_OP);
        String modified = String.format("%s %s", query, specific.getReturningKeys(keys));
        if (!batch) {
            logger.logSql(modified, (List<SqlParameter>) params);
        } else {
            logger.logSqlBatch(modified, (List<List<SqlParameter>>) params);
        }
        return modified;
    }

    private Object getGeneratedKey(ResultSet resultSet, Map.Entry<String, Class<?>> entry) throws SQLException {
        try {
            return SqlAccessor.find(entry.getValue())
                    .getGetter().get(resultSet, entry.getKey());
        } catch (SQLException ex) {
            GeneratedKeysSpecific specific = VendorSpecific.getSpecific(GeneratedKeysSpecific.class, GeneratedKeysSpecific.NO_OP);
            if (ex.getErrorCode() == 17023) {
                // We try with another approach
                ResultSetMetaData metaData = resultSet.getMetaData();
                for (int i = 0; i < metaData.getColumnCount(); i++) {
                    if (metaData.getColumnName(i + 1).equalsIgnoreCase(entry.getKey())) {
                        return resultSet.getObject(i + 1, entry.getValue());
                    }
                }
            } else if (specific.isCustomReturnKey()) {
                return specific.getReturningKey(resultSet, entry);
            }
            throw ex;
        }
    }

    public Connection getConnection(TableInfo tableInfo) throws SQLException {
        DataSourceProvider provider = DataSourceProvider.getCurrent();
        TransactionManager manager = TransactionManager.getInstance();
        if (manager instanceof TransactionManager.NoOpTransactionManager || manager.getCurrentTransaction() == null) {
            return isSchemaSupport(tableInfo) ? provider.getConnection(tableInfo) : provider.getConnection();
        } else {
            DataSourceProvider delegate = DataSourceProvider.getCurrentDelegate();
            if (delegate == null) {
                DataSourceProvider.setDelegate(manager.createDelegate(provider));
                return isSchemaSupport(tableInfo)
                        ? DataSourceProvider.getCurrentDelegate().getConnection(tableInfo)
                        : DataSourceProvider.getCurrentDelegate().getConnection();
            } else {
                return isSchemaSupport(tableInfo) ? delegate.getConnection(tableInfo) : delegate.getConnection();
            }
        }
    }

    private boolean isSchemaSupport(TableInfo tableInfo) {
        return !Table.UNSET.equalsIgnoreCase(tableInfo.getSchema());
    }

    public void registerUpdatedRows(Object object, int rows) {
        UPDATED_ROWS_LOCAL.get().put(object, rows);
    }

    public Integer getUpdatedRows(Object object) {
        return UPDATED_ROWS_LOCAL.get().get(object);
    }

    public abstract boolean isCompatible(Class<?> klass);
    public abstract boolean isSimple();

    public abstract <R> R read(Class<R> klass, String query, List<SqlParameter> params);
    public abstract <R> Result<R> readOpt(Class<R> klass, String query, List<SqlParameter> params);
    public abstract <R> List<R> readAll(Class<R> klass, String query, List<SqlParameter> params);
    public abstract <R> Stream<R> readStream(Class<R> klass, String query, List<SqlParameter> params);

    public abstract TableRow read(String query, List<SqlParameter> params);
    public abstract Optional<TableRow> readOpt(String query, List<SqlParameter> params);
    public abstract Stream<TableRow> readStream(String query, List<SqlParameter> params);

    public abstract <R> R insert(R entity, String query, List<SqlParameter> params);
    public abstract <R> List<R> insertWithBatch(Class<?> entityClass, String query, List<R> entities);
    public abstract int update(String query, List<SqlParameter> params);
    public abstract int delete(String query, List<SqlParameter> params);
    public abstract <R> List<R> updateWithBatch(Class<?> entityClass, String updateSql, List<R> entities);
}
