package com.frog.system.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.frog.system.domain.entity.SysUserNotificationPreference;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * 用户通知偏好表 Mapper 接口
 *
 * @author Deng
 * @since 2025-12-17
 */
@Mapper
@DS("notify")
public interface SysUserNotificationPreferenceMapper extends BaseMapper<SysUserNotificationPreference> {

    /**
     * 查询用户的所有通知偏好
     */
    @Select("""
            SELECT * FROM sys_user_notification_preference
            WHERE user_id = #{userId}
            """)
    List<SysUserNotificationPreference> findByUserId(@Param("userId") UUID userId);

    /**
     * 查询用户指定类型的通知偏好
     */
    @Select("""
            SELECT * FROM sys_user_notification_preference
            WHERE user_id = #{userId}
              AND notification_type = #{notificationType}
            """)
    List<SysUserNotificationPreference> findByUserIdAndType(@Param("userId") UUID userId,
                                                            @Param("notificationType") String notificationType);

    /**
     * 查询用户指定类型和渠道的通知偏好
     */
    @Select("""
            SELECT * FROM sys_user_notification_preference
            WHERE user_id = #{userId}
              AND notification_type = #{notificationType}
              AND channel = #{channel}
            """)
    SysUserNotificationPreference findByUserIdAndTypeAndChannel(@Param("userId") UUID userId,
                                                                @Param("notificationType") String notificationType,
                                                                @Param("channel") String channel);

    /**
     * 查询用户启用的通知渠道
     */
    @Select("""
            SELECT channel FROM sys_user_notification_preference
            WHERE user_id = #{userId}
              AND notification_type = #{notificationType}
              AND enabled = TRUE
            """)
    List<String> findEnabledChannelsByUserIdAndType(@Param("userId") UUID userId,
                                                    @Param("notificationType") String notificationType);

    /**
     * 删除用户的所有通知偏好
     */
    @Delete("""
            DELETE FROM sys_user_notification_preference
            WHERE user_id = #{userId}
            """)
    int deleteByUserId(@Param("userId") UUID userId);

    /**
     * 检查用户是否已配置指定偏好
     */
    @Select("""
            SELECT COUNT(*) > 0 FROM sys_user_notification_preference
            WHERE user_id = #{userId}
              AND notification_type = #{notificationType}
              AND channel = #{channel}
            """)
    boolean existsByUserIdAndTypeAndChannel(@Param("userId") UUID userId,
                                            @Param("notificationType") String notificationType,
                                            @Param("channel") String channel);
}