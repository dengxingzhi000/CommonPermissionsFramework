package com.frog.system.service.Impl;

import com.frog.common.dto.dept.DeptDTO;
import com.frog.common.exception.BusinessException;
import com.frog.common.util.UUIDv7Util;
import com.frog.common.web.util.SecurityUtils;
import com.frog.system.domain.entity.SysDept;
import com.frog.system.mapper.SysDeptMapper;
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

    /**
     * 查询部门树
     */
    @Override
    @Cacheable(value = "deptTree", key = "'all'")
    public List<DeptDTO> getDeptTree() {
        // 查询所有部门
        List<DeptDTO> allDepts = deptMapper.selectDeptTree();

        // 统计每个部门的用户数和子部门数
        for (DeptDTO dept : allDepts) {
            dept.setUserCount(deptMapper.countUsers(dept.getId()));
            dept.setChildCount(deptMapper.countChildren(dept.getId()));
        }

        // 构建树形结构
        return buildTree(allDepts);
    }

    /**
     * 查询子部门（包含自身）- 递归查询
     */
    @Override
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

        log.info("部门修改成功: {}, 操作人: {}", dept.getDeptName(),
                SecurityUtils.getCurrentUsername());
    }

    /**
     * 删除部门
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

        // 检查是否有用户
        if (hasUsers(id)) {
            Integer userCount = deptMapper.countUsers(id);
            throw new BusinessException("该部门下还有 " + userCount + " 个用户，不能删除");
        }

        deptMapper.deleteById(id);

        log.info("部门删除成功: {}, 操作人: {}", dept.getDeptName(),
                SecurityUtils.getCurrentUsername());
    }

    /**
     * 检查部门下是否有用户
     */
    @Override
    public boolean hasUsers(UUID deptId) {
        Integer count = deptMapper.countUsers(deptId);
        return count != null && count > 0;
    }

    /**
     * 检查部门下是否有子部门
     */
    @Override
    public boolean hasChildren(UUID deptId) {
        Integer count = deptMapper.countChildren(deptId);
        return count != null && count > 0;
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
