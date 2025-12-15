package com.frog.common.mybatisPlus.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;
import java.util.UUID;

/**
 * PostgreSQL UUID[] 数组类型处理器
 *
 * @author Deng
 * @since 2025-12-15
 */
@MappedTypes(UUID[].class)
@MappedJdbcTypes(JdbcType.ARRAY)
public class UuidArrayTypeHandler extends BaseTypeHandler<UUID[]> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, UUID[] parameter, JdbcType jdbcType)
            throws SQLException {
        Connection conn = ps.getConnection();
        Array array = conn.createArrayOf("uuid", parameter);
        ps.setArray(i, array);
    }

    @Override
    public UUID[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return getArray(rs.getArray(columnName));
    }

    @Override
    public UUID[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return getArray(rs.getArray(columnIndex));
    }

    @Override
    public UUID[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return getArray(cs.getArray(columnIndex));
    }

    private UUID[] getArray(Array array) throws SQLException {
        if (array == null) {
            return null;
        }
        Object obj = array.getArray();
        if (obj instanceof UUID[] uuids) {
            return uuids;
        }
        if (obj instanceof Object[] objArray) {
            UUID[] result = new UUID[objArray.length];
            for (int i = 0; i < objArray.length; i++) {
                if (objArray[i] instanceof UUID uuid) {
                    result[i] = uuid;
                } else if (objArray[i] instanceof String str) {
                    result[i] = UUID.fromString(str);
                } else if (objArray[i] != null) {
                    result[i] = UUID.fromString(objArray[i].toString());
                }
            }
            return result;
        }
        return null;
    }
}
