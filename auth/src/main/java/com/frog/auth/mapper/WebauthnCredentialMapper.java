package com.frog.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.frog.auth.domain.entity.WebauthnCredential;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.UUID;

/**
 * WebAuthn 凭证Mapper接口
 *
 * <p>该接口提供了对WebAuthn凭证数据的基本操作，包括：</p>
 * <ul>
 *   <li>查询用户的活跃凭证</li>
 *   <li>根据用户 ID和凭证 ID查询特定凭证</li>
 *   <li>更新凭证的签名计数和使用时间</li>
 *   <li>更新凭证关联的设备名称</li>
 *   <li>禁用或删除凭证</li>
 * </ul>
 *
 * <p>WebAuthn是一种现代的身份验证标准，允许用户使用生物识别、安全密钥等进行无密码登录。</p>
 */
@Mapper
public interface WebauthnCredentialMapper extends BaseMapper<WebauthnCredential> {
    /**
     * 根据用户 ID查询所有活跃凭证
     *
     * <p>此方法用于获取指定用户的所有未被禁用的WebAuthn凭证，
     * 结果按照创建时间倒序排列（最新的在前）。</p>
     *
     * @param userId 用户ID，不能为空
     * @return 用户的所有活跃凭证列表，如果不存在则返回空列表
     */
    @Select("""
            SELECT * FROM webauthn_credential
            WHERE user_id = #{userId} AND is_active = TRUE
            ORDER BY created_time DESC
            """)
    List<WebauthnCredential> findByUserId(@Param("userId") UUID userId);

    /**
     * 根据用户 ID和凭证 ID查询单个凭证
     *
     * <p>此方法用于精确查找一个特定的WebAuthn凭证，
     * 只有活跃状态（未被禁用）的凭证才会被返回。</p>
     *
     * @param userId 用户ID，不能为空
     * @param credentialId 凭证ID，不能为空
     * @return 匹配的凭证信息，如果不存在则返回null
     */
    @Select("""
            SELECT * FROM webauthn_credential
            WHERE user_id = #{userId} AND credential_id = #{credentialId} AND is_active = TRUE
            LIMIT 1
            """)
    WebauthnCredential findByUserIdAndCredId(@Param("userId") UUID userId,
                                             @Param("credentialId") String credentialId);

    /**
     * 更新签名计数器和最后使用时间
     *
     * <p>每次成功使用凭证进行身份验证后调用此方法，
     * 用于更新凭证的签名计数和最后使用时间戳。</p>
     *
     * @param userId 用户ID，不能为空
     * @param credentialId 凭证ID，不能为空
     * @param signCount 新的签名计数，通常比之前的值大
     */
    @Update("""
            UPDATE webauthn_credential
            SET sign_count = #{signCount}, last_used_at = NOW()
            WHERE user_id = #{userId} AND credential_id = #{credentialId}
            """)
    void updateSignCount(@Param("userId") UUID userId,
                         @Param("credentialId") String credentialId,
                         @Param("signCount") Long signCount);

    /**
     * 更新设备名称
     *
     * <p>允许用户为其注册的凭证设置易于识别的设备名称。</p>
     *
     * @param userId 用户ID，不能为空
     * @param credentialId 凭证ID，不能为空
     * @param deviceName 新的设备名称，不能为空
     * @return 影响的行数，正常情况下应为1
     */
    @Update("""
            UPDATE webauthn_credential
            SET device_name = #{deviceName}
            WHERE user_id = #{userId} AND credential_id = #{credentialId}
            """)
    int updateDeviceName(@Param("userId") UUID userId,
                         @Param("credentialId") String credentialId,
                         @Param("deviceName") String deviceName);

    /**
     * 禁用凭证(软删除)
     *
     * <p>当用户想要停用某个凭证而不完全删除它时使用。
     * 此操作将凭证标记为非活跃状态，使其无法再用于身份验证。</p>
     *
     * @param userId 用户ID，不能为空
     * @param credentialId 凭证ID，不能为空
     * @return 影响的行数，正常情况下应为1
     */
    @Update("""
            UPDATE webauthn_credential
            SET is_active = FALSE
            WHERE user_id = #{userId} AND credential_id = #{credentialId}
            """)
    int disableCredential(@Param("userId") UUID userId,
                          @Param("credentialId") String credentialId);

    /**
     * 删除凭证(硬删除)
     *
     * <p>从数据库中永久删除指定的凭证记录。
     * 注意：这是一个不可逆的操作。</p>
     *
     * @param userId 用户ID，不能为空
     * @param credentialId 凭证ID，不能为空
     * @return 影响的行数，正常情况下应为1
     */
    @Delete("""
            DELETE FROM webauthn_credential
            WHERE user_id = #{userId} AND credential_id = #{credentialId}
            """)
    int deleteByUserIdAndCredId(@Param("userId") UUID userId,
                                @Param("credentialId") String credentialId);

    /**
     * 列出用户所有活跃凭证(按最后使用时间排序)
     *
     * <p>获取用户的所有活跃凭证，并按照最后使用时间倒序排列，
     * 最近使用的凭证排在前面，从未使用过的凭证按创建时间倒序排列。</p>
     *
     * @param userId 用户ID，不能为空
     * @return 用户的所有活跃凭证列表，按使用频率排序，如果不存在则返回空列表
     */
    @Select("""
            SELECT * FROM webauthn_credential
            WHERE user_id = #{userId} AND is_active = TRUE
            ORDER BY last_used_at DESC NULLS LAST, created_time DESC
            """)
    List<WebauthnCredential> listActiveCredentials(@Param("userId") UUID userId);
}
