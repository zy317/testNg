package com.autotest.db;

import com.alibaba.fastjson.JSONObject;
import com.autotest.config.ConfigManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库助手 — 基于 HikariCP 连接池的数据库操作
 *
 * 使用工厂方法创建:
 *   DatabaseHelper db = DatabaseHelper.create("PERSONA");
 *
 * 按命名规则从 ConfigManager 读取 JDBC 连接信息:
 *   DB_{别名}_URL / DB_{别名}_USER / DB_{别名}_PASSWORD
 *
 * 使用 PreparedStatement 防 SQL 注入。
 */
public class DatabaseHelper {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHelper.class);

    /** HikariCP 连接池 */
    private final HikariDataSource dataSource;

    /** 数据库别名 */
    private final String alias;

    // ==================== 工厂方法 ====================

    /**
     * 创建数据库助手实例
     *
     * @param alias 数据库别名，如 "PERSONA"
     *              → 读取 DB_PERSONA_URL / DB_PERSONA_USER / DB_PERSONA_PASSWORD
     * @return DatabaseHelper 实例
     */
    public static DatabaseHelper create(String alias) {
        return new DatabaseHelper(alias);
    }

    /**
     * 私有构造，通过配置构建 HikariCP 连接池
     */
    private DatabaseHelper(String alias) {
        this.alias = alias;

        String url = ConfigManager.getValue("DB_" + alias + "_URL");
        String user = ConfigManager.getValue("DB_" + alias + "_USER");
        String password = ConfigManager.getValue("DB_" + alias + "_PASSWORD");

        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "数据库 [" + alias + "] 的 JDBC URL 未配置，请设置 DB_" + alias + "_URL");
        }
        if (user == null || user.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "数据库 [" + alias + "] 的用户名未配置，请设置 DB_" + alias + "_USER");
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password != null ? password : "");

        // 连接池配置
        config.setMinimumIdle(2);
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setConnectionTestQuery("SELECT 1");

        // MySQL 驱动配置
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new HikariDataSource(config);
        log.info("数据库 [{}] 连接池已创建: url={}, poolSize=2~10", alias, url);
    }

    // ==================== 查询操作 ====================

    /**
     * 执行查询 SQL，返回 List<JSONObject>
     *
     * 使用 PreparedStatement 防 SQL 注入。
     * 结果集中的每一行映射为一个 JSONObject（key 为列名，value 为列值）。
     *
     * @param sql    查询 SQL（带 ? 占位符）
     * @param params 参数（按顺序替换 ?）
     * @return 查询结果列表
     */
    public List<JSONObject> query(String sql, Object... params) {
        long startTime = System.currentTimeMillis();
        List<JSONObject> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // 设置参数
            setParameters(ps, params);
            log.debug("执行查询 SQL: {}", sql);

            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();

                while (rs.next()) {
                    JSONObject row = new JSONObject();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = meta.getColumnLabel(i);
                        Object value = rs.getObject(i);
                        row.put(columnName, value);
                    }
                    result.add(row);
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("查询完成: 返回 {} 行, 耗时 {}ms", result.size(), elapsed);

        } catch (SQLException e) {
            log.error("查询失败: sql={}, params={}, error={}", sql, params, e.getMessage(), e);
        }

        return result;
    }

    /**
     * 查询单个标量值（如 COUNT、SUM 等聚合结果的第一行第一列）
     *
     * @param sql    查询 SQL
     * @param params 参数
     * @return 标量值，无结果返回 null
     */
    public Object queryScalar(String sql, Object... params) {
        long startTime = System.currentTimeMillis();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            setParameters(ps, params);
            log.debug("执行标量查询: {}", sql);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Object result = rs.getObject(1);
                    long elapsed = System.currentTimeMillis() - startTime;
                    log.debug("标量查询完成: result={}, 耗时 {}ms", result, elapsed);
                    return result;
                }
            }

        } catch (SQLException e) {
            log.error("标量查询失败: sql={}, params={}, error={}", sql, params, e.getMessage(), e);
        }

        return null;
    }

    /**
     * 查询单个字符串标量值
     *
     * @param sql    查询 SQL
     * @param params 参数
     * @return 字符串标量值
     */
    public String queryScalarString(String sql, Object... params) {
        Object result = queryScalar(sql, params);
        return result != null ? result.toString() : null;
    }

    /**
     * 查询单个整数标量值
     *
     * @param sql    查询 SQL
     * @param params 参数
     * @return int 标量值，无结果返回 0
     */
    public int queryScalarInt(String sql, Object... params) {
        Object result = queryScalar(sql, params);
        if (result instanceof Number) {
            return ((Number) result).intValue();
        }
        if (result instanceof String) {
            try {
                return Integer.parseInt((String) result);
            } catch (NumberFormatException e) {
                log.warn("标量值无法转为 int: {}", result);
            }
        }
        return 0;
    }

    // ==================== 写操作 ====================

    /**
     * 执行 INSERT / UPDATE / DELETE 等写操作
     *
     * @param sql    写操作 SQL
     * @param params 参数
     * @return 影响的行数，失败返回 -1
     */
    public int executeUpdate(String sql, Object... params) {
        long startTime = System.currentTimeMillis();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            setParameters(ps, params);
            log.debug("执行写操作: {}", sql);

            int affected = ps.executeUpdate();
            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("写操作完成: 影响 {} 行, 耗时 {}ms", affected, elapsed);

            return affected;

        } catch (SQLException e) {
            log.error("写操作失败: sql={}, params={}, error={}", sql, params, e.getMessage(), e);
            return -1;
        }
    }

    /**
     * 批量执行写操作（事务中）
     *
     * @param sqlList SQL 列表
     * @return 总影响行数
     */
    public int executeBatch(List<String> sqlList) {
        int total = 0;
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                for (String sql : sqlList) {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        total += ps.executeUpdate();
                    }
                }
                conn.commit();
                log.debug("批量操作完成: {} 条 SQL, 影响 {} 行", sqlList.size(), total);
            } catch (SQLException e) {
                conn.rollback();
                log.error("批量操作回滚: {}", e.getMessage(), e);
                return -1;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("批量操作失败: {}", e.getMessage(), e);
            return -1;
        }
        return total;
    }

    // ==================== 连接池管理 ====================

    /**
     * 关闭数据库连接池
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("数据库 [{}] 连接池已关闭", alias);
        }
    }

    /**
     * 判断连接池是否可用
     */
    public boolean isAvailable() {
        try {
            return dataSource != null && !dataSource.isClosed()
                    && dataSource.getConnection() != null;
        } catch (SQLException e) {
            return false;
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 为 PreparedStatement 设置参数
     */
    private void setParameters(PreparedStatement ps, Object... params) throws SQLException {
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
        }
    }
}
