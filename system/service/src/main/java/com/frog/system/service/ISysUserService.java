package com.frog.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.frog.common.dto.user.UserDTO;
import com.frog.common.dto.user.UserInfo;
import com.frog.common.web.domain.SecurityUser;
import com.frog.system.domain.entity.SysUser;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * <p>
 * 用户表(UUID v7主键) 服务类
 * </p>
 *
 * @author author
 * @since 2025-10-14
 */
public interface ISysUserService extends IService<SysUser> {

    /**
     * 分页查询用户列表。
     *
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页数量
     * @param username 用户名（可选，支持模糊查询）
     * @param status   用户状态（可选，如：0-禁用，1-启用）
     * @return 用户分页数据
     */
    Page<UserDTO> listUsers(Integer pageNum, Integer pageSize,
                            String username, Integer status);

    /**
     * 根据用户名获取用户（用于 Spring Security 认证）。
     *
     * @param username 用户名
     * @return SecurityUser 用于认证的用户对象，包含密码、角色、权限等信息；如果用户不存在则返回 null
     */
    SecurityUser getUserByUsername(String username);

    /**
     * 根据用户ID获取用户详情。
     *
     * @param id 用户 ID
     * @return 用户详情
     */
    UserDTO getUserById(UUID id);

    /**
     * 获取用户的综合信息（含基础信息、角色、权限等）。
     *
     * @param userId 用户 ID
     * @return 用户综合信息
     */
    UserInfo getUserInfo(UUID userId);

    /**
     * 新增用户。
     *
     * @param userDTO 用户信息
     */
    void addUser(UserDTO userDTO);

    /**
     * 修改用户信息。
     *
     * @param userDTO 用户信息
     */
    void updateUser(UserDTO userDTO);

    /**
     * 删除用户。
     *
     * @param id 用户 ID
     */
    void deleteUser(UUID id);

    /**
     * 更新用户最近一次登录信息。
     *
     * @param userId    用户 ID
     * @param ipAddress 登录 IP 地址
     */
    void updateLastLogin(UUID userId, String ipAddress);

    /**
     * 重置用户密码，返回新生成的临时密码（或初始密码）。
     *
     * @param id 用户 ID
     * @return 新密码字符串
     */
    String resetPassword(UUID id);

    /**
     * 修改密码。
     *
     * @param userId     用户 ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     */
    void changePassword(UUID userId, String oldPassword, String newPassword);

    /**
     * 授予永久角色。
     *
     * @param userId  用户 ID
     * @param roleIds 角色 ID列表
     */
    void grantRoles(UUID userId, List<UUID> roleIds);

    /**
     * 授予临时角色（在有效期内生效）。
     *
     * @param userId        用户 ID
     * @param roleIds       角色 ID列表
     * @param effectiveTime 生效时间
     * @param expireTime    过期时间
     */
    void grantTemporaryRoles(UUID userId, List<UUID> roleIds,
                             LocalDateTime effectiveTime, LocalDateTime expireTime);

    /**
     * 延长临时角色的有效期。
     *
     * @param userId       用户 ID
     * @param roleId       角色 ID
     * @param newExpireTime 新的过期时间
     */
    void extendTemporaryRole(UUID userId, UUID roleId, LocalDateTime newExpireTime);

    /**
     * 提前终止临时角色。
     *
     * @param userId 用户 ID
     * @param roleId 角色 ID
     */
    void terminateTemporaryRole(UUID userId, UUID roleId);

    /**
     * 查询用户的临时角色列表。
     *
     * @param userId 用户 ID
     * @return 临时角色列表（包含角色与有效期等信息）
     */
    List<Map<String, Object>> getUserTemporaryRoles(UUID userId);

    /**
     * 锁定或解锁用户。
     *
     * @param id   用户 ID
     * @param lock 是否锁定（true 锁定；false 解锁）
     */
    void lockUser(UUID id, Boolean lock);

    /**
     * 检查用户是否有访问指定部门数据的权限。
     *
     * @param userId 用户 ID
     * @param deptId 部门 ID
     * @return true 有访问权限；false 无访问权限
     */
    boolean canAccessDept(UUID userId, UUID deptId);

    /**
     * 获取用户的数据权限范围。
     *
     * @param userId 用户 ID
     * @return 数据范围标识（例如：0-仅本人，1-本部门，2-本部门及以下，3-全部等）
     */
    Integer getUserDataScope(UUID userId);

    /**
     * 统计用户相关信息（登录次数、角色数量、权限数量等）。
     *
     * @param userId 用户 ID
     * @return 统计结果键值对
     */
    Map<String, Object> getUserStatistics(UUID userId);
}
