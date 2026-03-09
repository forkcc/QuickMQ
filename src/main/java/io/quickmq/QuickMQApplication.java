package io.quickmq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * QuickMQ 启动入口。Spring Boot 3 + Netty MQTT + Hazelcast 骨架，不启动 web 容器。
 */
@SpringBootApplication
public class QuickMQApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(QuickMQApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.run(args);
    }
}
