package com.frog.system.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.frog.system.domain.entity.SysUser;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 用户表 Mapper 接口
 * <p>
 * 注意：此 Mapper 只处理 db_user 库中的 sys_user 表
 * 涉及角色、权限的查询请使用对应的 Mapper 或 Service 层聚合
 *
 * @author Deng
 * @since 2025-11-03
 */
@Mapper
@DS("user")
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 根据用户名查询用户
     */
    @Select("""
            SELECT * FROM sys_user
            WHERE username = #{username} AND NOT deleted
            """)
    SysUser findByUsername(@Param("username") String username);

    /**
     * 检查用户名是否存在
     */
    @Select("""
            SELECT COUNT(*) > 0 FROM sys_user
            WHERE username = #{username} AND NOT deleted
            """)
    boolean existsByUsername(@Param("username") String username);

    /**
     * 更新最后登录信息
     */
    @Update("""
            UPDATE sys_user SET
                last_login_time = #{loginTime},
                last_login_ip = #{ipAddress},
                login_attempts = 0
            WHERE id = #{userId}
            """)
    void updateLastLogin(@Param("userId") UUID userId,
                         @Param("ipAddress") String ipAddress,
                         @Param("loginTime") LocalDateTime loginTime);

    /**
     * 增加登录尝试次数
     */
    @Update("""
            UPDATE sys_user SET login_attempts = login_attempts + 1
            WHERE username = #{username}
            """)
    void incrementLoginAttempts(@Param("username") String username);

    /**
     * 锁定账户
     */
    @Update("""
            UPDATE sys_user
            SET status = 2,
                locked_until = #{lockedUntil}
            WHERE username = #{username}
            """)
    void lockAccount(@Param("username") String username,
                     @Param("lockedUntil") LocalDateTime lockedUntil);

    /**
     * 根据用户 ID列表批量查询用户基本信息
     */
    @Select("""
            <script>
            SELECT id, username, real_name, email, dept_id FROM sys_user
            WHERE id IN
            <foreach collection='userIds' item='id' open='(' close=')' separator=','>
                #{id}
            </foreach>
            AND NOT deleted
            </script>
            """)
    List<SysUser> selectBasicInfoByIds(@Param("userIds") List<UUID> userIds);

    /**
     * 根据部门 ID查询用户ID列表
     */
    @Select("""
            SELECT id FROM sys_user
            WHERE dept_id = #{deptId} AND NOT deleted
            """)
    List<UUID> findUserIdsByDeptId(@Param("deptId") UUID deptId);

    /**
     * 获取用户的部门 ID
     */
    @Select("""
            SELECT dept_id FROM sys_user
            WHERE id = #{userId} AND NOT deleted
            """)
    UUID getUserDeptId(@Param("userId") UUID userId);

    /**
     * 批量统计多个部门的用户数量
     * <p>
     * 用于优化 getDeptTree 等需要统计多个部门用户数的场景
     */
    @Select("""
            <script>
            SELECT dept_id, COUNT(*) as user_count FROM sys_user
            WHERE dept_id IN
            <foreach collection='deptIds' item='deptId' open='(' close=')' separator=','>
                #{deptId}
            </foreach>
            AND NOT deleted
            GROUP BY dept_id
            </script>
            """)
    @MapKey("dept_id")
    Map<UUID, Map<String, Object>> countUsersByDeptIds(@Param("deptIds") List<UUID> deptIds);

    /**
     * 统计单个部门的用户数
     */
    @Select("""
            SELECT COUNT(*) FROM sys_user
            WHERE dept_id = #{deptId} AND NOT deleted
            """)
    int countUsersByDeptId(@Param("deptId") UUID deptId);
}
