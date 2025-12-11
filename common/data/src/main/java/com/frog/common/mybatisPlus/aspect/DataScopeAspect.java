package com.frog.common.mybatisPlus.aspect;

import com.frog.common.mybatisPlus.annotation.DataScope;
import com.frog.common.mybatisPlus.context.DataScopeContextHolder;
import com.frog.common.web.domain.SecurityUser;
import com.frog.common.web.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.UUID;
/**
 * 数据权限切面
 * 根据用户角色的dataScope自动注入SQL过滤条件
 *
 * @author Deng
 * createData 2025/10/30 11:15
 * @version 1.0
 */
@Aspect
@Component
@Slf4j
public class DataScopeAspect {

    /**
     * 拦截带有@DataScope注解的方法
     */
    @Around("@annotation(dataScope)")
    public Object around(ProceedingJoinPoint point, DataScope dataScope) throws Throwable {
        try {
            SecurityUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return point.proceed();
            }

            UUID userId = currentUser.getUserId();
            UUID deptId = currentUser.getDeptId();

            // 获取用户的数据权限范围
            Integer dataScopeLevel = currentUser.getAccountType();

            if (dataScopeLevel == null) {
                dataScopeLevel = 5; // 默认仅本人
            }

            String sqlFilter = buildSqlFilter(dataScopeLevel, userId, deptId, dataScope);

            // 设置到ThreadLocal，由DataScopeInterceptor使用
            DataScopeContextHolder.set(sqlFilter);

            log.debug("Data scope applied: userId={}, level={}, filter={}",
                    userId, dataScopeLevel, sqlFilter);

            return point.proceed();
        } finally {
            // 清理ThreadLocal
            DataScopeContextHolder.clear();
        }
    }

    /**
     * 构建SQL过滤条件
     * 适配您的MySQL BINARY(16) UUID存储
     */
    private String buildSqlFilter(Integer dataScope, UUID userId, UUID deptId, DataScope annotation) {
        String deptAlias = annotation.deptAlias();
        String userAlias = annotation.userAlias();

        return switch (dataScope) {
            case 1 -> // 全部数据权限
                    "1=1";

            case 2 -> // 自定义数据权限（从数据库查询配置）
                    buildCustomDataScope(userId, deptAlias, userAlias);

            case 3 -> // 本部门数据权限
                    deptId != null
                            ? String.format("%s = UNHEX(REPLACE('%s', '-', ''))", deptAlias, deptId)
                            : "1=0";

            case 4 -> // 本部门及以下数据权限
                    deptId != null
                            ? buildDeptAndChildrenScope(deptId, deptAlias)
                            : "1=0";

            case 5 -> // 仅本人数据权限
                    String.format("%s = UNHEX(REPLACE('%s', '-', ''))", userAlias, userId);

            default ->
                    "1=0"; // 无权限
        };
    }

    /**
     * 构建自定义数据权限
     */
    private String buildCustomDataScope(UUID userId, String deptAlias, String userAlias) {
        // TODO: 从sys_role_data_permission表查询用户的自定义权限规则
        // 这里简化为仅本人
        return String.format("%s = UNHEX(REPLACE('%s', '-', ''))", userAlias, userId);
    }

    /**
     * 构建部门及子部门权限
     */
    private String buildDeptAndChildrenScope(UUID deptId, String deptAlias) {
        // 使用递归CTE查询所有子部门
        return String.format(
                            """
                            %s IN (WITH RECURSIVE dept_tree AS (
                              SELECT id FROM sys_dept WHERE id = UNHEX(REPLACE('%s', '-', ''))
                              UNION ALL
                              SELECT d.id FROM sys_dept d INNER JOIN dept_tree dt ON d.parent_id = dt.id
                            ) SELECT HEX(id) FROM dept_tree)
                            """,
                deptAlias, deptId
        );
    }
}
