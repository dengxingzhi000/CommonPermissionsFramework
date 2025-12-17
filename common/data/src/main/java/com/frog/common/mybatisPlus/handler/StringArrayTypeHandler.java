package com.frog.common.mybatisPlus.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;

/**
 * PostgreSQL TEXT[] 数组类型处理器
 *
 * @author Deng
 * @since 2025-12-15
 */
@MappedTypes(String[].class)
@MappedJdbcTypes(JdbcType.ARRAY)
public class StringArrayTypeHandler extends BaseTypeHandler<String[]> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String[] parameter, JdbcType jdbcType)
            throws SQLException {
        Connection conn = ps.getConnection();
        Array array = conn.createArrayOf("text", parameter);
        ps.setArray(i, array);
    }

    @Override
    public String[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return getArray(rs.getArray(columnName));
    }

    @Override
    public String[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return getArray(rs.getArray(columnIndex));
    }

    @Override
    public String[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return getArray(cs.getArray(columnIndex));
    }

    private String[] getArray(Array array) throws SQLException {
        if (array == null) {
            return null;
        }
        Object obj = array.getArray();
        if (obj instanceof String[] strings) {
            return strings;
        }
        if (obj instanceof Object[] objArray) {
            String[] result = new String[objArray.length];
            for (int i = 0; i < objArray.length; i++) {
                result[i] = objArray[i] != null ? objArray[i].toString() : null;
            }
            return result;
        }
        return null;
    }
}
