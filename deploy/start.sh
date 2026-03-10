#!/bin/bash
# ==========================================================================
# QuickMQ 生产启动脚本（面向百万连接优化）
# ==========================================================================
set -euo pipefail

APP_JAR="${1:-target/quickmq-*.jar}"
if [[ "$APP_JAR" == *"*"* ]]; then
    APP_JAR=$(ls $APP_JAR 2>/dev/null | head -1)
fi

if [[ ! -f "$APP_JAR" ]]; then
    echo "ERROR: 找不到 JAR: $APP_JAR"
    echo "用法: $0 [jar-path]"
    exit 1
fi

# ---------- JVM 内存 ----------
HEAP="-Xms4g -Xmx4g"
DIRECT="-XX:MaxDirectMemorySize=12g"

# ---------- GC ----------
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
