package com.frog.system.sync.handler;

import com.frog.common.integration.sync.event.DataSyncEvent;
import com.frog.common.integration.sync.handler.DataSyncHandler;
import com.frog.system.sync.executor.DeptSyncExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
    private final DeptSyncExecutor syncExecutor;

    @Override
    public String getAggregateType() {
        return "Dept";
    }

    @Override
    public void handle(DataSyncEvent event) throws DataSyncHandler.DataSyncException {
        UUID deptId = UUID.fromString(event.getPrimaryId());
        Map<String, Object> data = event.getAfterData();

        log.debug("[DeptSync] Handling event: deptId={}, type={}", deptId, event.getEventType());

        try {
            switch (event.getEventType()) {
                case INSERT, UPDATE -> syncDeptName(deptId, data);
                case DELETE -> log.info("[DeptSync] Dept deleted, keeping redundant data for audit: {}", deptId);
                default -> log.warn("[DeptSync] Unknown event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            throw new DataSyncHandler.DataSyncException("Failed to sync dept: " + deptId, e, true);
        }
    }

    private void syncDeptName(UUID deptId, Map<String, Object> data) {
        String deptName = (String) data.get("deptName");
        // 通过独立的 Bean 调用，确保 @Transactional 和 @DS 生效
        syncExecutor.syncToAuditDb(deptId, deptName);
        syncExecutor.syncToApprovalDb(deptId, deptName);
    }
}