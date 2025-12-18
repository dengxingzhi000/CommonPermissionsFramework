package com.frog.auth.domain.dto;

import com.frog.auth.domain.entity.WebauthnCredential;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * WebAuthn 凭证转换器
 * <p>
 * 实现Entity和DTO之间的转换
 * 隐藏敏感信息，保护数据安全
 *
 * @author system
 * @since 2025-11-27
 */
@Component
public class WebauthnCredentialConverter {
    /**
     * 将实体转换为DTO
     * 移除敏感信息（公钥等）
     *
     * @param entity 实体对象
     * @return DTO 对象
     */
    public WebauthnCredentialDTO toDTO(WebauthnCredential entity) {
        if (entity == null) {
            return null;
        }

        return WebauthnCredentialDTO.builder()
                .credentialId(entity.getCredentialId())
                .deviceName(entity.getDeviceName())
                .algorithm(entity.getAlg())
                .aaguid(entity.getAaguid())
                .transports(entity.getTransports() != null ? String.join(",", entity.getTransports()) : null)
                .authenticatorAttachment(null) // Not stored in database
                .isActive(entity.getIsActive())
                .backupState(null) // Not stored in database
                .lastUsedAt(entity.getLastUsedAt())
                .createdTime(entity.getCreateTime())
                .build();
    }

    /**
     * 批量转换
     *
     * @param entities 实体列表
     * @return DTO 列表
     */
    public List<WebauthnCredentialDTO> toDTOList(List<WebauthnCredential> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 将注册请求转换为实体
     *
     * @param request 注册请求
     * @param userId  用户 ID
     * @return 实体对象
     */
    public WebauthnCredential toEntity(WebauthnRegistrationRequest request, UUID userId) {
        if (request == null) {
            return null;
        }

        WebauthnCredential entity = new WebauthnCredential();
        entity.setCredentialId(request.getCredentialId());
        entity.setUserId(userId);
        entity.setPublicKeyPem(request.getPublicKeyPem());
        entity.setAlg(request.getAlgorithm());
        entity.setSignCount(0L); // 初始计数器
        entity.setDeviceName(request.getDeviceName());
        entity.setAaguid(request.getAaguid());
        entity.setTransports(request.getTransports() != null ? request.getTransports().split(",") : null);
        entity.setIsActive(true); // 默认启用

        return entity;
    }
}