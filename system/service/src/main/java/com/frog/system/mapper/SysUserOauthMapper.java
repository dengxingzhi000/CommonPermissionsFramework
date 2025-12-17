package com.frog.system.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.frog.system.domain.entity.SysUserOauth;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.UUID;

/**
 * OAuth第三方登录绑定 Mapper 接口
 *
 * @author Deng
 * @since 2025-12-15
 */
@Mapper
@DS("user")
public interface SysUserOauthMapper extends BaseMapper<SysUserOauth> {

    /**
     * 根据 OAuth提供商和OpenID查询绑定信息
     */
    @Select("""
            SELECT * FROM sys_user_oauth
            WHERE provider = #{provider} AND oauth_openid = #{openid} AND NOT deleted
            """)
    SysUserOauth findByProviderAndOpenid(@Param("provider") String provider, @Param("openid") String openid);

    /**
     * 根据用户 ID查询所有OAuth绑定
     */
    @Select("""
            SELECT * FROM sys_user_oauth
            WHERE user_id = #{userId} AND NOT deleted
            """)
    List<SysUserOauth> findByUserId(@Param("userId") UUID userId);

    /**
     * 根据用户 ID和提供商查询绑定信息
     */
    @Select("""
            SELECT * FROM sys_user_oauth
            WHERE user_id = #{userId} AND provider = #{provider} AND NOT deleted
            """)
    SysUserOauth findByUserIdAndProvider(@Param("userId") UUID userId, @Param("provider") String provider);

    /**
     * 检查用户是否已绑定指定提供商
     */
    @Select("""
            SELECT COUNT(*) > 0 FROM sys_user_oauth
            WHERE user_id = #{userId} AND provider = #{provider} AND NOT deleted
            """)
    boolean existsByUserIdAndProvider(@Param("userId") UUID userId, @Param("provider") String provider);

    /**
     * 更新最后登录时间
     */
    @Update("""
            UPDATE sys_user_oauth
            SET last_login_time = NOW(), update_time = NOW()
            WHERE id = #{id}
            """)
    int updateLastLoginTime(@Param("id") UUID id);

    /**
     * 解绑 OAuth账号
     */
    @Update("""
            UPDATE sys_user_oauth
            SET deleted = true, update_time = NOW()
            WHERE user_id = #{userId} AND provider = #{provider}
            """)
    int unbind(@Param("userId") UUID userId, @Param("provider") String provider);
}
