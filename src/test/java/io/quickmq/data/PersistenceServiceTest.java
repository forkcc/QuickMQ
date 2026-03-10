package io.quickmq.data;

import io.quickmq.data.entity.ClientSessionEntity;
import io.quickmq.data.entity.RetainedMessageEntity;
import io.quickmq.data.entity.SubscriptionEntity;
import io.quickmq.data.repository.ClientSessionRepository;
import io.quickmq.data.repository.RetainedMessageRepository;
import io.quickmq.data.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(PersistenceService.class)
@EnableJpaRepositories(basePackages = "io.quickmq.data.repository")
@EntityScan(basePackages = "io.quickmq.data.entity")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:hsqldb:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.hsqldb.jdbc.JDBCDriver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.HSQLDialect"
})
class PersistenceServiceTest {

    @Autowired
    private PersistenceService service;

    @Autowired
    private ClientSessionRepository sessionRepo;

    @Autowired
    private SubscriptionRepository subscriptionRepo;

    @Autowired
    private RetainedMessageRepository retainedRepo;

    // ==================== Session ====================

    @Test
    void saveAndFindSession() {
        service.saveSession("c1", "admin", false);

        Optional<ClientSessionEntity> found = sessionRepo.findByClientId("c1");
        assertTrue(found.isPresent());
        assertEquals("admin", found.get().getUsername());
        assertFalse(found.get().isCleanSession());
    }

    @Test
    void saveSession_upserts() {
        service.saveSession("c1", "user1", false);
        service.saveSession("c1", "user2", true);

        Optional<ClientSessionEntity> found = sessionRepo.findByClientId("c1");
        assertTrue(found.isPresent());
        assertEquals("user2", found.get().getUsername());
        assertTrue(found.get().isCleanSession());
    }

    @Test
    void deleteSession() {
        service.saveSession("c1", "user", false);
        service.deleteSession("c1");
        assertTrue(sessionRepo.findByClientId("c1").isEmpty());
    }

    // ==================== Subscription ====================

    @Test
    void saveAndFindSubscription() {
        service.saveSubscription("c1", "sensor/#", 1);

        List<SubscriptionEntity> subs = subscriptionRepo.findByClientId("c1");
        assertEquals(1, subs.size());
        assertEquals("sensor/#", subs.get(0).getTopicFilter());
        assertEquals(1, subs.get(0).getQos());
    }

    @Test
    void saveSubscription_upserts() {
        service.saveSubscription("c1", "a/b", 0);
        service.saveSubscription("c1", "a/b", 2);

        List<SubscriptionEntity> subs = subscriptionRepo.findByClientId("c1");
        assertEquals(1, subs.size());
        assertEquals(2, subs.get(0).getQos());
    }

    @Test
    void deleteSubscription() {
        service.saveSubscription("c1", "a/b", 0);
        service.saveSubscription("c1", "x/y", 1);
        service.deleteSubscription("c1", "a/b");

        List<SubscriptionEntity> subs = subscriptionRepo.findByClientId("c1");
        assertEquals(1, subs.size());
        assertEquals("x/y", subs.get(0).getTopicFilter());
    }

    @Test
    void deleteAllSubscriptions() {
        service.saveSubscription("c1", "a/b", 0);
        service.saveSubscription("c1", "x/y", 1);
        service.deleteAllSubscriptions("c1");
        assertTrue(subscriptionRepo.findByClientId("c1").isEmpty());
    }

    @Test
    void findSubscriptions() {
        service.saveSubscription("c1", "a/b", 0);
        service.saveSubscription("c1", "x/y", 1);
        service.saveSubscription("c2", "z/w", 2);

        List<SubscriptionEntity> result = service.findSubscriptions("c1");
        assertEquals(2, result.size());
    }

    // ==================== Retained Message ====================

    @Test
    void saveAndFindRetained() {
        service.saveRetained("sensor/temp", "25.5".getBytes(), 1);

        Optional<RetainedMessageEntity> found = retainedRepo.findByTopic("sensor/temp");
        assertTrue(found.isPresent());
        assertArrayEquals("25.5".getBytes(), found.get().getPayload());
        assertEquals(1, found.get().getQos());
    }

    @Test
    void saveRetained_upserts() {
        service.saveRetained("a/b", "old".getBytes(), 0);
        service.saveRetained("a/b", "new".getBytes(), 1);

        List<RetainedMessageEntity> all = retainedRepo.findAll();
        assertEquals(1, all.size());
        assertArrayEquals("new".getBytes(), all.get(0).getPayload());
    }

    @Test
    void deleteRetained() {
        service.saveRetained("a/b", "data".getBytes(), 0);
        service.deleteRetained("a/b");
        assertTrue(retainedRepo.findByTopic("a/b").isEmpty());
    }
}
