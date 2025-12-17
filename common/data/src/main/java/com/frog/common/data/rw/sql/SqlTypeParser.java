package com.frog.common.data.rw.sql;

import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * SQL 类型解析器
 * <p>
 * 通过 SQL 语句判断是读操作还是写操作
 *
 * @author Deng
 * @since 2025-12-16
 */
@Slf4j
public class SqlTypeParser {
    /**
     * 写操作关键字
     */
    private static final Set<String> WRITE_KEYWORDS = Set.of(
            "INSERT", "UPDATE", "DELETE", "REPLACE",
            "CREATE", "ALTER", "DROP", "TRUNCATE",
            "GRANT", "REVOKE", "LOCK", "UNLOCK",
            "CALL", "MERGE", "UPSERT"
    );

    /**
     * SELECT ... FOR UPDATE 模式
     */
    private static final Pattern FOR_UPDATE_PATTERN =
            Pattern.compile(".*\\bFOR\\s+UPDATE\\b.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * SELECT ... LOCK IN SHARE MODE 模式
     */
    private static final Pattern LOCK_IN_SHARE_MODE_PATTERN =
            Pattern.compile(".*\\bLOCK\\s+IN\\s+SHARE\\s+MODE\\b.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * Hint: 强制主库
     */
    private static final Pattern MASTER_HINT_PATTERN =
            Pattern.compile("/\\*\\s*MASTER\\s*\\*/", Pattern.CASE_INSENSITIVE);

    /**
     * Hint: 强制从库
     */
    private static final Pattern SLAVE_HINT_PATTERN =
            Pattern.compile("/\\*\\s*SLAVE\\s*\\*/", Pattern.CASE_INSENSITIVE);

    /**
     * Hint: 指定从库名称
     */
    private static final Pattern SLAVE_NAME_HINT_PATTERN =
            Pattern.compile("/\\*\\s*SLAVE\\s*\\(\\s*(\\w+)\\s*\\)\\s*\\*/", Pattern.CASE_INSENSITIVE);

    /**
     * SQL 类型
     */
    public enum SqlType {
        READ,
        WRITE,
        UNKNOWN
    }

    /**
     * 路由 Hint
     */
    public record RoutingHint(
            HintType type,
            String slaveName
    ) {
        public enum HintType {
            NONE,
            MASTER,
            SLAVE
        }
    }

    /**
     * 解析 SQL 类型
     *
     * @param sql SQL 语句
     * @return SQL 类型
     */
    public static SqlType parse(String sql) {
        if (sql == null || sql.isBlank()) {
            return SqlType.UNKNOWN;
        }

        String trimmedSql = sql.trim();

        // 1. 检查是否有 FOR UPDATE / LOCK IN SHARE MODE（需要走主库）
        if (FOR_UPDATE_PATTERN.matcher(trimmedSql).matches() ||
                LOCK_IN_SHARE_MODE_PATTERN.matcher(trimmedSql).matches()) {
            log.trace("[SQL-Parser] Detected locking SQL, type: WRITE");
            return SqlType.WRITE;
        }

        // 2. 获取第一个关键字
        String firstKeyword = getFirstKeyword(trimmedSql);

        // 3. 判断是否是写操作
        if (WRITE_KEYWORDS.contains(firstKeyword)) {
            log.trace("[SQL-Parser] Detected write keyword [{}], type: WRITE", firstKeyword);
            return SqlType.WRITE;
        }

        // 4. SELECT 开头视为读操作
        if ("SELECT".equals(firstKeyword) || "SHOW".equals(firstKeyword) ||
                "DESCRIBE".equals(firstKeyword) || "EXPLAIN".equals(firstKeyword)) {
            log.trace("[SQL-Parser] Detected read keyword [{}], type: READ", firstKeyword);
            return SqlType.READ;
        }

        log.trace("[SQL-Parser] Unknown SQL type for keyword [{}]", firstKeyword);
        return SqlType.UNKNOWN;
    }

    /**
     * 解析路由 Hint
     * <p>
     * 支持格式：
     * - /*MASTER* / SELECT ... → 强制主库
     * - /*SLAVE* / SELECT ... → 强制从库（负载均衡选择）
     * - /*SLAVE(slave1)* / SELECT ... → 指定从库
     *
     * @param sql SQL 语句
     * @return 路由 Hint
     */
    public static RoutingHint parseHint(String sql) {
        if (sql == null || sql.isBlank()) {
            return new RoutingHint(RoutingHint.HintType.NONE, null);
        }

        // 检查 MASTER Hint
        if (MASTER_HINT_PATTERN.matcher(sql).find()) {
            log.trace("[SQL-Parser] Found MASTER hint");
            return new RoutingHint(RoutingHint.HintType.MASTER, null);
        }

        // 检查带名称的 SLAVE Hint
        var slaveNameMatcher = SLAVE_NAME_HINT_PATTERN.matcher(sql);
        if (slaveNameMatcher.find()) {
            String slaveName = slaveNameMatcher.group(1);
            log.trace("[SQL-Parser] Found SLAVE hint with name: {}", slaveName);
            return new RoutingHint(RoutingHint.HintType.SLAVE, slaveName);
        }

        // 检查 SLAVE Hint
        if (SLAVE_HINT_PATTERN.matcher(sql).find()) {
            log.trace("[SQL-Parser] Found SLAVE hint");
            return new RoutingHint(RoutingHint.HintType.SLAVE, null);
        }

        return new RoutingHint(RoutingHint.HintType.NONE, null);
    }

    /**
     * 移除 SQL 中的 Hint 注释
     */
    public static String removeHint(String sql) {
        if (sql == null) {
            return null;
        }
        return sql
                .replaceAll("/\\*\\s*MASTER\\s*\\*/", "")
                .replaceAll("/\\*\\s*SLAVE\\s*(\\(\\s*\\w+\\s*\\))?\\s*\\*/", "")
                .trim();
    }

    /**
     * 获取 SQL 的第一个关键字
     */
    private static String getFirstKeyword(String sql) {
        // 跳过注释和空白
        String cleanSql = sql
                .replaceAll("/\\*.*?\\*/", "")  // 移除块注释
                .replaceAll("--.*$", "")         // 移除行注释
                .replaceAll("^\\s+", "");        // 移除开头空白

        // 获取第一个词
        int spaceIndex = cleanSql.indexOf(' ');
        int newlineIndex = cleanSql.indexOf('\n');
        int tabIndex = cleanSql.indexOf('\t');

        int endIndex = cleanSql.length();
        if (spaceIndex > 0) endIndex = Math.min(endIndex, spaceIndex);
        if (newlineIndex > 0) endIndex = Math.min(endIndex, newlineIndex);
        if (tabIndex > 0) endIndex = Math.min(endIndex, tabIndex);

        return cleanSql.substring(0, endIndex).toUpperCase();
    }

    /**
     * 判断是否是事务控制语句
     */
    public static boolean isTransactionStatement(String sql) {
        if (sql == null) {
            return false;
        }
        String upper = sql.trim().toUpperCase();
        return upper.startsWith("BEGIN") ||
                upper.startsWith("START TRANSACTION") ||
                upper.startsWith("COMMIT") ||
                upper.startsWith("ROLLBACK") ||
                upper.startsWith("SAVEPOINT") ||
                upper.startsWith("SET AUTOCOMMIT");
    }
}
