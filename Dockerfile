# ==================== 构建阶段 ====================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY src src
RUN apk add --no-cache maven \
    && mvn clean package -DskipTests -q \
    && mv target/quickmq-*.jar app.jar

# ==================== 运行阶段 ====================
FROM eclipse-temurin:21-jre-alpine
LABEL maintainer="QuickMQ" description="High-performance MQTT Broker"

RUN addgroup -S quickmq && adduser -S quickmq -G quickmq
WORKDIR /app
COPY --from=builder /build/app.jar app.jar
COPY deploy/sysctl-tuning.conf /etc/sysctl.d/99-quickmq.conf

RUN mkdir -p /app/data && chown -R quickmq:quickmq /app
USER quickmq

EXPOSE 1883 8083

ENV JAVA_OPTS="-Xms2g -Xmx2g \
  -XX:MaxDirectMemorySize=4g \
  -XX:+UseZGC -XX:+ZGenerational \
  -XX:+AlwaysPreTouch \
  -Dio.netty.leakDetection.level=DISABLED \
  -Dio.netty.recycler.maxCapacityPerThread=4096 \
  -Djava.security.egd=file:/dev/./urandom"

VOLUME /app/data

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar $0 $@"]
