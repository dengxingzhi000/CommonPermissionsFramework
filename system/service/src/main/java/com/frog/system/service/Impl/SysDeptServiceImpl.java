package com.frog.system.service.Impl;

import com.frog.common.dto.dept.DeptDTO;
import com.frog.common.exception.BusinessException;
import com.frog.common.util.UUIDv7Util;
import com.frog.common.web.util.SecurityUtils;
import com.frog.common.data.rw.annotation.Slave;
import com.frog.system.domain.entity.SysDept;
import com.frog.system.event.DataSyncEventPublisher;
import com.frog.system.mapper.SysDeptMapper;
import com.frog.system.service.CrossDatabaseQueryService;
import com.frog.system.service.ISysDeptService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
/**
 * <p>
 * 部门表 服务实现类
 * </p>
 *
 * @author author
 * @since 2025-11-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept> implements ISysDeptService {
    private final SysDeptMapper deptMapper;
    private final CrossDatabaseQueryService crossDatabaseQueryService;
    private final DataSyncEventPublisher dataSyncEventPublisher;

    /**
     * 查询部门树
     * <p>
     * 注意：此方法使用冗余字段获取负责人信息（无需跨库）
     * 用户数统计通过 CrossDatabaseQueryService 批量跨库查询 db_user 实现（性能优化）
     * <p>
     * 查询走从库
     */
    @Override
    @Slave
    @Cacheable(value = "deptTree", key = "'all'")
    public List<DeptDTO> getDeptTree() {
        // 1. 从 org 库查询所有部门（使用冗余字段包含负责人信息）
        List<SysDept> depts = deptMapper.selectDeptTreeWithLeader();

        // 2. 收集所有部门 ID，用于批量统计用户数
        List<UUID> deptIds = depts.stream().map(SysDept::getId).toList();

        // 3. 通过 CrossDatabaseQueryService 从 user 库批量统计每个部门的用户数（单次跨库查询，性能优化）
        Map<UUID, Integer> userCountMap = new HashMap<>();
        if (!deptIds.isEmpty()) {
            userCountMap = crossDatabaseQueryService.countUsersByDeptIds(deptIds);
        }

        // 4. 批量统计每个部门的子部门数（避免 N+1 查询）
        Map<UUID, Integer> childCountMap = new HashMap<>();
        if (!deptIds.isEmpty()) {
            Map<UUID, Map<String, Object>> childCountResult = deptMapper.countChildrenByDeptIds(deptIds);
            if (childCountResult != null) {
                childCountResult.forEach((parentId, row) -> {
                    Object count = row.get("child_count");
                    childCountMap.put(parentId, count != null ? ((Number) count).intValue() : 0);
                });
            }
        }

        // 5. 转换为 DTO 并填充统计信息
        Map<UUID, Integer> finalUserCountMap = userCountMap;
        List<DeptDTO> allDepts = depts.stream().map(dept -> {
            DeptDTO dto = new DeptDTO();
            BeanUtils.copyProperties(dept, dto);
            dto.setLeaderName(dept.getLeaderName());  // 使用冗余字段
            dto.setUserCount(finalUserCountMap.getOrDefault(dept.getId(), 0));
            dto.setChildCount(childCountMap.getOrDefault(dept.getId(), 0));
            return dto;
        }).toList();

        // 6. 构建树形结构
        return buildTree(new ArrayList<>(allDepts));
    }

    /**
     * 查询子部门（包含自身）- 递归查询
     * <p>
     * 查询走从库
     */
    @Override
    @Slave
    @Cacheable(value = "deptChildren", key = "#deptId")
    public List<UUID> getDeptAndChildren(UUID deptId) {
        return deptMapper.selectDeptAndChildren(deptId);
    }

    /**
     * 新增部门
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"deptTree", "deptChildren"}, allEntries = true)
    public void addDept(DeptDTO deptDTO) {
        // 1. 校验部门编码唯一性
        if (deptMapper.existsByDeptCode(deptDTO.getDeptCode())) {
            throw new BusinessException("部门编码已存在");
        }

        // 2. 校验父部门是否存在
        if (deptDTO.getParentId() != null && !deptDTO.getParentId().equals(
                UUID.fromString("00000000-0000-0000-0000-000000000000"))) {
            SysDept parent = deptMapper.selectById(deptDTO.getParentId());
            if (parent == null) {
                throw new BusinessException("父部门不存在");
            }
        }

        // 3. 转换并保存
        SysDept dept = new SysDept();
        BeanUtils.copyProperties(deptDTO, dept);
        dept.setId(UUIDv7Util.generate());

        deptMapper.insert(dept);

        // Publish sync event for redundancy update
        dataSyncEventPublisher.publishDeptCreated(dept);

        log.info("部门创建成功: {}, 操作人: {}", dept.getDeptName(),
                SecurityUtils.getCurrentUsername());
    }

    /**
     * 修改部门
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"deptTree", "deptChildren"}, allEntries = true)
    public void updateDept(DeptDTO deptDTO) {
        SysDept existDept = deptMapper.selectById(deptDTO.getId());
        if (existDept == null) {
            throw new BusinessException("部门不存在");
        }

        // 不能将父部门设置为自己或自己的子部门
        if (deptDTO.getParentId() != null) {
            if (deptDTO.getParentId().equals(deptDTO.getId())) {
                throw new BusinessException("父部门不能是自己");
            }

            // 检查是否是子部门
            List<UUID> children = deptMapper.selectDeptAndChildren(deptDTO.getId());
            if (children.contains(deptDTO.getParentId())) {
                throw new BusinessException("父部门不能是自己的子部门");
            }
        }

        SysDept dept = new SysDept();
        BeanUtils.copyProperties(deptDTO, dept);

        deptMapper.updateById(dept);

        // Publish sync event for redundancy update
        SysDept updatedDept = deptMapper.selectById(dept.getId());
        dataSyncEventPublisher.publishDeptUpdated(updatedDept);

        log.info("部门修改成功: {}, 操作人: {}", dept.getDeptName(),
                SecurityUtils.getCurrentUsername());
    }

    /**
     * 删除部门
     * <p>
     * 删除部门时会同时清理以下关联数据：
     * <ul>
     *   <li>sys_role_dept (db_permission) - 角色与部门的数据权限关联</li>
     * </ul>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"deptTree", "deptChildren"}, allEntries = true)
    public void deleteDept(UUID id) {
        SysDept dept = deptMapper.selectById(id);
        if (dept == null) {
            throw new BusinessException("部门不存在");
        }

        // 检查是否有子部门
        if (hasChildren(id)) {
            throw new BusinessException("该部门下还有子部门，不能删除");
        }

        // 检查是否有用户（跨库查询 db_user）
        int userCount = countUsersByDeptId(id);
        if (userCount > 0) {
            throw new BusinessException("该部门下还有 " + userCount + " 个用户，不能删除");
        }

        // 1. 清理角色部门关联数据 (sys_role_dept in db_permission) - 跨库操作
        int deletedRoleDeptCount = crossDatabaseQueryService.deleteRoleDeptsByDeptId(id);
        if (deletedRoleDeptCount > 0) {
            log.debug("已清理部门 {} 的角色关联记录: {} 条", dept.getDeptName(), deletedRoleDeptCount);
        }

        // 2. 删除部门记录
        deptMapper.deleteById(id);

        // 3. 发布同步事件用于冗余数据更新
        dataSyncEventPublisher.publishDeptDeleted(id);

        log.info("部门删除成功: {}, 操作人: {}", dept.getDeptName(),
                SecurityUtils.getCurrentUsername());
    }

    /**
     * 检查部门下是否有用户
     * <p>
     * 跨库查询 db_user
     */
    @Override
    public boolean hasUsers(UUID deptId) {
        return countUsersByDeptId(deptId) > 0;
    }

    /**
     * 检查部门下是否有子部门
     */
    @Override
    public boolean hasChildren(UUID deptId) {
        Integer count = deptMapper.countChildren(deptId);
        return count != null && count > 0;
    }

    /**
     * 统计部门下的用户数
     * <p>
     * 通过 CrossDatabaseQueryService 跨库查询 db_user
     */
    private int countUsersByDeptId(UUID deptId) {
        return crossDatabaseQueryService.countUsersByDeptId(deptId);
    }

    // ========== 私有方法 ==========

    /**
     * 构建树形结构
     */
    private List<DeptDTO> buildTree(List<DeptDTO> depts) {
        Map<UUID, DeptDTO> deptMap = new HashMap<>();
        for (DeptDTO dept : depts) {
            deptMap.put(dept.getId(), dept);
        }

        List<DeptDTO> tree = new ArrayList<>();
        for (DeptDTO dept : depts) {
            // 根节点
            if (dept.getParentId() == null ||
                    dept.getParentId().equals(UUID.fromString("00000000-0000-0000-0000-000000000000"))) {
                buildTreeChildren(dept, deptMap);
                tree.add(dept);
            }
        }

        return tree;
    }

    /**
     * 递归构建子节点
     */
    private void buildTreeChildren(DeptDTO parent, Map<UUID, DeptDTO> deptMap) {
        List<DeptDTO> children = new ArrayList<>();
        for (DeptDTO dept : deptMap.values()) {
            if (dept.getParentId() != null && dept.getParentId().equals(parent.getId())) {
                buildTreeChildren(dept, deptMap);
                children.add(dept);
            }
        }
        parent.setChildren(children);
    }
}
