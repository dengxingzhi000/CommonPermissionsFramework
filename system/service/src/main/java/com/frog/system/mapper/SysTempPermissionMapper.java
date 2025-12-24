package com.frog.system.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.frog.system.domain.entity.SysTempPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.UUID;

/**
 * 临时权限表 Mapper 接口
 *
 * @author Deng
 * @since 2025-12-17
 */
@Mapper
@DS("permission")
public interface SysTempPermissionMapper extends BaseMapper<SysTempPermission> {

    /**
     * 查询用户有效的临时权限
     */
    @Select("""
            SELECT * FROM sys_temp_permission
            WHERE user_id = #{userId}
              AND status = 1
              AND effective_time <= NOW()
              AND expire_time > NOW()
            """)
    List<SysTempPermission> findEffectiveByUserId(@Param("userId") UUID userId);

    /**
     * 查询用户有效的临时权限 ID 列表
     */
    @Select("""
            SELECT permission_id FROM sys_temp_permission
            WHERE user_id = #{userId}
              AND status = 1
              AND effective_time <= NOW()
              AND expire_time > NOW()
            """)
    List<UUID> findEffectivePermissionIdsByUserId(@Param("userId") UUID userId);

    /**
     * 查询即将过期的临时权限（用于清理任务）
     */
    @Select("""
            SELECT * FROM sys_temp_permission
            WHERE status = 1
              AND expire_time <= NOW()
            """)
    List<SysTempPermission> findExpired();

    /**
     * 禁用过期的临时权限
     */
    @Update("""
            UPDATE sys_temp_permission
            SET status = 0
            WHERE status = 1
              AND expire_time <= NOW()
            """)
    int disableExpired();

    /**
     * 根据审批 ID 查询临时权限
     */
    @Select("""
            SELECT * FROM sys_temp_permission
            WHERE approval_id = #{approvalId}
            """)
    List<SysTempPermission> findByApprovalId(@Param("approvalId") UUID approvalId);

    /**
     * 统计正在使用指定权限的临时授权数量
     * <p>
     * 用于权限删除前检查，防止意外删除正在被使用的权限
     */
    @Select("""
            SELECT COUNT(*) FROM sys_temp_permission
            WHERE permission_id = #{permissionId}
              AND status = 1
              AND expire_time > NOW()
            """)
    Integer countActiveByPermissionId(@Param("permissionId") UUID permissionId);
}