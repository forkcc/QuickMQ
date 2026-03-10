package io.quickmq.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 服务器状态管理器，用于控制服务器是否接受新连接。
 */
public class ServerStatusManager {
    
    private static final Logger log = LoggerFactory.getLogger(ServerStatusManager.class);
    private final AtomicBoolean acceptingConnections = new AtomicBoolean(true);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    
    public boolean isAcceptingConnections() {
        return acceptingConnections.get() && !shuttingDown.get();
    }
    
    public boolean isShuttingDown() {
        return shuttingDown.get();
    }
    
    public void setAcceptingConnections(boolean accepting) {
        boolean old = acceptingConnections.getAndSet(accepting);
        if (old != accepting) {
            log.info("服务器连接接受状态变更: {}", accepting ? "接受新连接" : "拒绝新连接");
        }
    }
    
    public void startShutdown() {
        if (shuttingDown.compareAndSet(false, true)) {
            log.info("服务器开始关闭，拒绝所有新连接");
        }
    }
    
    public void stopShutdown() {
        if (shuttingDown.compareAndSet(true, false)) {
            log.info("服务器取消关闭，恢复接受连接");
        }
    }
}