package com.frog.gateway.util;

import lombok.Getter;
import lombok.NonNull;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import reactor.core.publisher.Flux;

/**
 * Simple request decorator that keeps a copy of the request body for multiple reads.
 * 简单的请求装饰器，保留请求正文的副本以供多次读取。
 */
@Getter
public class CachedBodyRequestDecorator extends ServerHttpRequestDecorator {
    private static final DataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

    private final byte[] cachedBody;

    public CachedBodyRequestDecorator(ServerHttpRequest delegate, byte[] cachedBody) {
        super(delegate);
        this.cachedBody = cachedBody != null ? cachedBody : new byte[0];
    }

    @Override
    @NonNull
    public Flux<DataBuffer> getBody() {
        return Flux.defer(() -> {
            byte[] copy = new byte[cachedBody.length];
            System.arraycopy(cachedBody, 0, copy, 0, cachedBody.length);
            DataBuffer buffer = BUFFER_FACTORY.wrap(copy);
            return Flux.just(buffer);
        });
    }
}
