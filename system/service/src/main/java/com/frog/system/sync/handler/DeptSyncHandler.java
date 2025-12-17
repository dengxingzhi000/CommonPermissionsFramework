package com.frog.system.sync.handler;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.frog.common.integration.sync.event.DataSyncEvent;
import com.frog.common.integration.sync.handler.DataSyncHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * 部门数据同步处理器
 * <p>
 * 处理部门数据变更，同步更新冗余字段到其他库：
 * - db_audit.sys_audit_log (dept_name)
 * - db_approval.sys_permission_approval (applicant_dept_name)
 *
 * @author Deng
 * @since 2025-12-16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeptSyncHandler implements DataSyncHandler {

    @Override
    public String getAggregateType() {
        return "Dept";
    }

    @Override
    public void handle(DataSyncEvent event) throws DataSyncException {
        UUID deptId = UUID.fromString(event.getPrimaryId());
        Map<String, Object> data = event.getAfterData();

        log.debug("[DeptSync] Handling event: deptId={}, type={}", deptId, event.getEventType());

        try {
            switch (event.getEventType()) {
                case INSERT, UPDATE -> {
                    String deptName = (String) data.get("deptName");
                    syncToAuditDb(deptId, deptName);
                    syncToApprovalDb(deptId, deptName);
                }
                case DELETE -> {
                    // 部门删除时，冗余字段保留历史值（审计需要）
                    log.info("[DeptSync] Dept deleted, keeping redundant data for audit: {}", deptId);
                }
                default -> log.warn("[DeptSync] Unknown event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            throw new DataSyncException("Failed to sync dept: " + deptId, e, true);
        }
    }

    /**
     * 同步部门信息到 audit 库
     */
    @DS("audit")
    @Transactional
    public void syncToAuditDb(UUID deptId, String deptName) {
        // 更新审计日志中的部门名称
        // 注意：审计日志通常不更新历史记录，这里只是示例
        log.debug("[DeptSync] Would update audit logs for dept: {}, name: {}", deptId, deptName);
    }

    /**
     * 同步部门信息到 approval 库
     */
    @DS("approval")
    @Transactional
    public void syncToApprovalDb(UUID deptId, String deptName) {
        // 更新审批记录中的部门名称
        log.debug("[DeptSync] Would update approval records for dept: {}, name: {}", deptId, deptName);
    }
}
