package com.frog.common.dto.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 *
 *
 * @author Deng
 * createData 2025/10/15 11:32
 * @version 1.0
 */
@Data
public class RefreshTokenRequest {
    @NotBlank(message = "刷新令牌不能为空")
    private String refreshToken;

    private String deviceId;
}
