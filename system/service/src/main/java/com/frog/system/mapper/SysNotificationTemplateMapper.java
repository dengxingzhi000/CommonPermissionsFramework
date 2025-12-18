package com.frog.system.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.frog.system.domain.entity.SysNotificationTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 通知模板表 Mapper 接口
 *
 * @author Deng
 * @since 2025-12-17
 */
@Mapper
@DS("notify")
public interface SysNotificationTemplateMapper extends BaseMapper<SysNotificationTemplate> {

    /**
     * 根据模板编码查询
     */
    @Select("""
            SELECT * FROM sys_notification_template
            WHERE template_code = #{templateCode}
              AND NOT deleted
            """)
    SysNotificationTemplate findByTemplateCode(@Param("templateCode") String templateCode);

    /**
     * 根据模板编码和渠道查询
     */
    @Select("""
            SELECT * FROM sys_notification_template
            WHERE template_code = #{templateCode}
              AND channel = #{channel}
              AND NOT deleted
            """)
    SysNotificationTemplate findByTemplateCodeAndChannel(@Param("templateCode") String templateCode,
                                                         @Param("channel") String channel);

    /**
     * 查询指定渠道的所有启用模板
     */
    @Select("""
            SELECT * FROM sys_notification_template
            WHERE channel = #{channel}
              AND status = 1
              AND NOT deleted
            ORDER BY template_code
            """)
    List<SysNotificationTemplate> findActiveByChannel(@Param("channel") String channel);

    /**
     * 检查模板编码是否存在
     */
    @Select("""
            SELECT COUNT(*) > 0 FROM sys_notification_template
            WHERE template_code = #{templateCode}
              AND NOT deleted
            """)
    boolean existsByTemplateCode(@Param("templateCode") String templateCode);
}