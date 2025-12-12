package com.frog.common.mybatisPlus.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.frog.common.security.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 字段自动填充处理器
 *
 * @author Deng
 * createData 2025/10/15 14:37
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
public class MyMetaObjectHandler implements MetaObjectHandler {
    private final SecurityContext securityContext;

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "createBy", UUID.class,
                securityContext.getCurrentUserId());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateBy", UUID.class,
                securityContext.getCurrentUserId());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, "updateBy", UUID.class,
                securityContext.getCurrentUserId());
    }
}
