package com.frog.common.feign.reload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 证书热更新加载器（参考 Netflix Lemur 设计）
 * 支持零停机更新 mTLS 证书
 *
 * @author Deng
 * @version 1.0
 */
@Slf4j
@RequiredArgsConstructor
public class CertificateReloader {
    private final Resource keystoreResource;
    private final String keystorePassword;
    private final Resource truststoreResource;
    private final String truststorePassword;

    private final AtomicReference<SSLContext> sslContextRef = new AtomicReference<>();
    private final AtomicReference<X509TrustManager> trustManagerRef = new AtomicReference<>();

    // 证书文件最后修改时间缓存
    private volatile FileTime lastKeystoreModified;
    private volatile FileTime lastTruststoreModified;

    // 监听器列表（用于通知 Feign Client 重建连接）
    private final CopyOnWriteArrayList<CertificateReloadListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * 初始化加载证书
     */
    public void initialize() throws Exception {
        loadCertificates(true);
        updateLastModifiedTimes();
        log.info("证书初始化加载完成");
    }

    /**
     * 定时检查证书文件变化（每分钟检查一次）
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void checkAndReload() {
        try {
            boolean keystoreChanged = hasFileChanged(keystoreResource, lastKeystoreModified);
            boolean truststoreChanged = hasFileChanged(truststoreResource, lastTruststoreModified);

            if (keystoreChanged || truststoreChanged) {
                log.info("检测到证书文件变化，开始热更新... (keystore: {}, truststore: {})",
                        keystoreChanged, truststoreChanged);

                loadCertificates(false);
                updateLastModifiedTimes();
                notifyListeners();

                log.info("证书热更新成功，无需重启应用");
            }
        } catch (Exception e) {
            log.error("证书热更新失败，继续使用旧证书: {}", e.getMessage(), e);
            // 保留旧证书，不影响现有连接
        }
    }

    /**
     * 加载证书并构建 SSLContext
     */
    private void loadCertificates(boolean isInitial) throws Exception {
        KeyStore keyStore = loadKeyStore(keystoreResource, keystorePassword);
        KeyStore trustStore = loadKeyStore(truststoreResource, truststorePassword);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keystorePassword.toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        X509TrustManager x509Tm = extractX509TrustManager(tmf.getTrustManagers());
        if (x509Tm == null) {
            throw new IllegalStateException("未找到 X509TrustManager");
        }

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), new TrustManager[]{x509Tm}, new SecureRandom());

        sslContextRef.set(sslContext);
        trustManagerRef.set(x509Tm);

        if (!isInitial) {
            log.info("SSLContext 已更新");
        }
    }

    /**
     * 加载 KeyStore
     */
    private KeyStore loadKeyStore(Resource resource, String password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = resource.getInputStream()) {
            keyStore.load(is, password.toCharArray());
        }
        return keyStore;
    }

    /**
     * 提取 X509TrustManager
     */
    private X509TrustManager extractX509TrustManager(TrustManager[] trustManagers) {
        for (TrustManager tm : trustManagers) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }
        return null;
    }

    /**
     * 检查文件是否变化
     */
    private boolean hasFileChanged(Resource resource, FileTime lastModified) throws IOException {
        Path path = getFilePath(resource);
        if (path == null || !Files.exists(path)) {
            return false;
        }

        FileTime currentModified = Files.getLastModifiedTime(path);
        return lastModified == null || currentModified.compareTo(lastModified) > 0;
    }

    /**
     * 更新最后修改时间缓存
     */
    private void updateLastModifiedTimes() throws IOException {
        Path keystorePath = getFilePath(keystoreResource);
        Path truststorePath = getFilePath(truststoreResource);

        if (keystorePath != null && Files.exists(keystorePath)) {
            lastKeystoreModified = Files.getLastModifiedTime(keystorePath);
        }
        if (truststorePath != null && Files.exists(truststorePath)) {
            lastTruststoreModified = Files.getLastModifiedTime(truststorePath);
        }
    }

    /**
     * 获取 Resource 的文件路径
     */
    private Path getFilePath(Resource resource) throws IOException {
        try {
            return Paths.get(resource.getURI());
        } catch (Exception e) {
            log.warn("无法获取资源文件路径: {}", resource.getDescription());
            return null;
        }
    }

    /**
     * 注册重载监听器
     */
    public void addListener(CertificateReloadListener listener) {
        listeners.add(listener);
    }

    /**
     * 通知所有监听器
     */
    private void notifyListeners() {
        for (CertificateReloadListener listener : listeners) {
            try {
                listener.onCertificateReloaded();
            } catch (Exception e) {
                log.error("通知监听器失败: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 获取当前 SSLContext
     */
    public SSLContext getSslContext() {
        return sslContextRef.get();
    }

    /**
     * 获取当前 TrustManager
     */
    public X509TrustManager getTrustManager() {
        return trustManagerRef.get();
    }

    /**
     * 证书重载监听器接口
     */
    @FunctionalInterface
    public interface CertificateReloadListener {
        void onCertificateReloaded();
    }
}