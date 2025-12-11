package com.frog.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.frog.system.domain.entity.NotificationAuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationAuditLogMapper extends BaseMapper<NotificationAuditLog> {
}

