#!/bin/bash
# ==========================================================================
# QuickMQ 生产启动脚本（面向百万连接优化）
# ==========================================================================
set -euo pipefail

APP_JAR="${1:-target/quickmq-*.jar}"
# 如果传了确切路径用确切路径，否则 glob 解析
if [[ "$APP_JAR" == *"*"* ]]; then
    APP_JAR=$(ls $APP_JAR 2>/dev/null | head -1)
fi

if [[ ! -f "$APP_JAR" ]]; then
    echo "ERROR: 找不到 JAR: $APP_JAR"
    echo "用法: $0 [jar-path]"
    exit 1
fi

# ---------- JVM 内存 ----------
# 百万长连接，每连接 ~10-15KB Netty 开销 + 业务对象
# 堆：4-8GB 足够（大部分内存在 off-heap DirectMemory）
# DirectMemory：每连接 ~8KB pipeline buffer = 8GB，保守给 12GB
HEAP="-Xms4g -Xmx4g"
DIRECT="-XX:MaxDirectMemorySize=12g"

# ---------- GC ----------
# ZGC: 低延迟，适合长连接 broker（亚毫秒停顿）
# 如果 JDK < 17 可换 G1GC: -XX:+UseG1GC -XX:MaxGCPauseMillis=50
GC="-XX:+UseZGC -XX:+ZGenerational"

# ---------- Netty 优化 ----------
NETTY_OPTS=""
NETTY_OPTS="$NETTY_OPTS -Dio.netty.leakDetection.level=DISABLED"
NETTY_OPTS="$NETTY_OPTS -Dio.netty.recycler.maxCapacityPerThread=4096"
NETTY_OPTS="$NETTY_OPTS -Dio.netty.allocator.numDirectArenas=$(nproc)"
NETTY_OPTS="$NETTY_OPTS -Dio.netty.eventLoopThreads=$(($(nproc) * 2))"

# ---------- JVM 杂项 ----------
MISC=""
MISC="$MISC -server"
MISC="$MISC -XX:+AlwaysPreTouch"
MISC="$MISC -XX:+UseNUMA"
MISC="$MISC -Djava.security.egd=file:/dev/./urandom"

echo "=========================================="
echo " QuickMQ Broker 启动"
echo " JAR:    $APP_JAR"
echo " HEAP:   $HEAP"
echo " DIRECT: $DIRECT"
echo " GC:     $GC"
echo " CPUs:   $(nproc)"
echo "=========================================="

exec java \
    $HEAP \
    $DIRECT \
    $GC \
    $NETTY_OPTS \
    $MISC \
    -jar "$APP_JAR" \
    "$@"
