package com.frog.common.security.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.frog.common.security.annotation.EncryptField;
import com.frog.common.security.annotation.Sensitive;
import com.frog.common.security.enums.SensitiveType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
/**
 * 用户DTO - 带数据脱敏和加密
 *
 * @author Deng
 * createData 2025/10/30 11:44
 * @version 1.0
 */
@Data
public class UserDTOWithSensitive {
    private UUID id;

    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 32, message = "用户名长度4-32位")
    private String username;

    @NotBlank(message = "真实姓名不能为空")
    @Sensitive(type = SensitiveType.NAME)  // 姓名脱敏：张**
    private String realName;

    @EncryptField  // 数据库存储加密
    @Sensitive(type = SensitiveType.ID_CARD)  // 响应脱敏：110101********1234
    @Pattern(
            regexp = "^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]$",
            message = "身份证号格式不正确"
    )
    private String idCard;

    @Sensitive(type = SensitiveType.EMAIL)  // 邮箱脱敏：abc****@example.com
    @Email(message = "邮箱格式不正确")
    private String email;

    @Sensitive(type = SensitiveType.MOBILE)  // 手机号脱敏：138****1234
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @EncryptField  // 银行卡号加密存储
    @Sensitive(type = SensitiveType.BANK_CARD)  // 响应脱敏：6222 **** **** 1234
    private String bankCard;

    @Sensitive(type = SensitiveType.ADDRESS)  // 地址脱敏：保留前6位
    private String address;

    private String avatar;
    private Integer status;
    private UUID deptId;
    private String deptName;
    private Integer userLevel;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastLoginTime;

    private String lastLoginIp;
    private List<UUID> roleIds;
    private List<String> roleNames;
}