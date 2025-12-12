package com.frog.common.util;

import com.github.f4b6a3.uuid.UuidCreator;
import com.github.f4b6a3.uuid.util.UuidUtil;

import java.util.UUID;

/**
 * UUIDv7版本工具类
 *
 * @author Deng
 * createData 2025/10/17 14:35
 * @version 1.0
 */
public class UUIDv7Util {
    /**
     * 生成UUIDv7
     */
    public static UUID generate() {
        return UuidCreator.getTimeOrderedEpoch();
    }

    /**
     * 生成UUIDv7字符串
     */
    public static String generateString() {
        return generate().toString();
    }

    /**
     * 生成UUIDv7字符串（无连字符）
     */
    public static String generateCompact() {
        return generateString().replace("-", "");
    }

    /**
     * 从UUIDv7提取时间戳
     */
    public static long extractTimestamp(UUID uuid) {
        return UuidUtil.getInstant(uuid).toEpochMilli();
    }
}
