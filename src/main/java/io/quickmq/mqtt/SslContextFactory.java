package io.quickmq.mqtt;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.quickmq.config.MqttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;

/**
 * SSL 上下文工厂，用于创建 Netty SslContext。
 */
public class SslContextFactory {
    
    private static final Logger log = LoggerFactory.getLogger(SslContextFactory.class);
    
    private final MqttProperties properties;
    private SslContext sslContext;
    
    public SslContextFactory(MqttProperties properties) {
        this.properties = properties;
    }
    
    public SslContext getSslContext() {
        if (sslContext == null) {
            sslContext = createSslContext();
        }
        return sslContext;
    }
    
    private SslContext createSslContext() {
        String certPath = properties.getSslCertPath();
        String keyPath = properties.getSslKeyPath();
        
        if (certPath.isEmpty() || keyPath.isEmpty()) {
            log.warn("SSL 证书或私钥路径为空，SSL 功能禁用");
            return null;
        }
        
        try {
            SslContextBuilder builder = SslContextBuilder.forServer(
                    new java.io.File(certPath),
                    new java.io.File(keyPath)
            );
            
            String trustCertPath = properties.getSslTrustCertPath();
            if (!trustCertPath.isEmpty()) {
                try (InputStream trustStream = new FileInputStream(trustCertPath)) {
                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    Collection<? extends Certificate> certs = cf.generateCertificates(trustStream);
                    
                    KeyStore trustStore = KeyStore.getInstance("JKS");
                    trustStore.load(null, null);
                    int i = 0;
                    for (Certificate cert : certs) {
                        trustStore.setCertificateEntry("cert-" + i++, cert);
                    }
                    
                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                            TrustManagerFactory.getDefaultAlgorithm());
                    tmf.init(trustStore);
                    builder.trustManager(tmf);
                }
            }
            
            if (properties.isSslClientAuth()) {
                builder.clientAuth(io.netty.handler.ssl.ClientAuth.REQUIRE);
            }
            
            SslContext context = builder.build();
            log.info("SSL 上下文初始化成功: cert={}, key={}, clientAuth={}",
                    certPath, keyPath, properties.isSslClientAuth());
            return context;
            
        } catch (Exception e) {
            log.error("SSL 上下文初始化失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    public boolean isSslEnabled() {
        return !properties.getSslCertPath().isEmpty() && 
               !properties.getSslKeyPath().isEmpty() &&
               getSslContext() != null;
    }
}