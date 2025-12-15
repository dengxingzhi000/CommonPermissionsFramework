package com.frog.common.util;

import com.github.f4b6a3.uuid.UuidCreator;
import com.github.f4b6a3.uuid.util.UuidUtil;

import java.util.UUID;

/**
 * UUID v7版本工具类
 * 提供基于时间戳的UUID v7生成和解析功能
 *
 * @author Deng
 * createData 2025/10/17 14:35
 * @version 1.0
 */
public class UUIDv7Util {
    /**
     * 生成UUID
     * 基于当前时间戳生成有序的UUID，适用于数据库主键等场景
     * 
     * @return UUID 实例
     */
    public static UUID generate() {
        return UuidCreator.getTimeOrderedEpoch();
    }

    /**
     * 生成UUID 字符串
     * 
     * @return 标准格式的UUID字符串（包含连字符）
     */
    public static String generateString() {
        return generate().toString();
    }

    /**
     * 生成UUID字符串（无连字符）
     * 
     * @return 紧凑格式的UUID字符串（不含连字符）
     */
    public static String generateCompact() {
        return generateString().replace("-", "");
    }

    /**
     * 从UUID提取时间戳
     * 注意：此方法仅适用于UUID格式，对其他版本UUID可能抛出异常
     * 
     * @param uuid UUID 实例
     * @return 时间戳（毫秒）
     */
    public static long extractTimestamp(UUID uuid) {
        return UuidUtil.getInstant(uuid).toEpochMilli();
    }
}
