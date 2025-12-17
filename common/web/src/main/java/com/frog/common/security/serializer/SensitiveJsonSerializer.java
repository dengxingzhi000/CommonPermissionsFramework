package com.frog.common.security.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.frog.common.security.annotation.Sensitive;
import com.frog.common.security.enums.SensitiveType;

import java.io.IOException;
/**
 * 敏感数据序列化器
 * 在JSON序列化时自动脱敏
 *
 * @author Deng
 * createData 2025/10/30 11:24
 * @version 1.0
 */
public class SensitiveJsonSerializer extends JsonSerializer<String>
        implements ContextualSerializer {
    private SensitiveType type;
    private boolean enabled = true;

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (!enabled || value == null || value.isEmpty()) {
            gen.writeString(value);
            return;
        }

        // 执行脱敏
        String desensitizedValue = type.desensitize(value);
        gen.writeString(desensitizedValue);
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property)
            throws JsonMappingException {
        if (property == null) {
            return prov.findNullValueSerializer(null);
        }

        // 获取字段上的@Sensitive注解
        Sensitive sensitive = property.getAnnotation(Sensitive.class);
        if (sensitive == null) {
            return prov.findValueSerializer(property.getType(), property);
        }

        // 创建新的序列化器实例
        SensitiveJsonSerializer serializer = new SensitiveJsonSerializer();
        serializer.type = sensitive.type();
        serializer.enabled = sensitive.enabled();
        return serializer;
    }
}
