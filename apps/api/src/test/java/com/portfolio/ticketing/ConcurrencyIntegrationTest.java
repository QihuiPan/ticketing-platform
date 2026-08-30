package com.portfolio.ticketing;

import com.portfolio.ticketing.api.ApiException;
import com.portfolio.ticketing.api.ApiModels;
import com.portfolio.ticketing.domain.DomainTypes;
import com.portfolio.ticketing.domain.EventEntity;
import com.portfolio.ticketing.domain.EventSessionEntity;
import com.portfolio.ticketing.domain.SeatEntity;
import com.portfolio.ticketing.domain.UserAccount;
import com.portfolio.ticketing.repository.EventRepository;
import com.portfolio.ticketing.repository.EventSessionRepository;
import com.portfolio.ticketing.repository.OutboxEventRepository;
import com.portfolio.ticketing.repository.PaymentRepository;
import com.portfolio.ticketing.repository.SeatHoldRepository;
import com.portfolio.ticketing.repository.SeatRepository;
import com.portfolio.ticketing.repository.UserAccountRepository;
import com.portfolio.ticketing.service.HoldService;
import com.portfolio.ticketing.service.OrderService;
import com.portfolio.ticketing.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.seed-demo=false",
        "app.holds.ttl=PT5M",
        "app.scheduling.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@Testcontainers(disabledWithoutDocker = true)
class ConcurrencyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> 6399);
    }

    @Autowired
    UserAccountRepository users;

    @Autowired
    EventRepository events;

    @Autowired
    EventSessionRepository sessions;

    @Autowired
    SeatRepository seats;

    @Autowired
    SeatHoldRepository holds;

    @Autowired
    PaymentRepository payments;

    @Autowired
    OutboxEventRepository outbox;

    @Autowired
    HoldService holdService;

    @Autowired
    OrderService orderService;

    @Autowired
    PaymentService paymentService;

    @Autowired
    JdbcTemplate jdbc;

    UserAccount buyer;
    SeatEntity seat;

    @BeforeEach
    void createFixture() {
        UserAccount organizer = users.save(new UserAccount(
                uniqueEmail("organizer"), "hash", DomainTypes.Role.ORGANIZER));
        buyer = users.save(new UserAccount(uniqueEmail("buyer"), "hash", DomainTypes.Role.BUYER));
        EventEntity event = new EventEntity(organizer, "Contention test", "Integration fixture");
        event.publish();
        events.save(event);
        EventSessionEntity session = sessions.save(new EventSessionEntity(
                event, Instant.now().plus(1, ChronoUnit.DAYS), "Test venue"));
        seat = seats.save(new SeatEntity(session, "A-01", new BigDecimal("49.00")));
    }

    @Test
    void oneOfOneHundredConcurrentAttemptsWinsTheSeat() throws Exception {
        int contenders = 100;
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> outcomes = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(contenders)) {
            for (int index = 0; index < contenders; index++) {
                outcomes.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        holdService.create(seat.getId(), buyer.getId());
                        return true;
                    } catch (ApiException conflict) {
                        return false;
                    }
                }));
            }
            ready.await();
            start.countDown();
            long successful = 0;
            for (Future<Boolean> outcome : outcomes) {
                if (outcome.get()) successful++;
            }
            assertThat(successful).isEqualTo(1);
        }

        assertThat(holds.findBySeatIdAndStatus(seat.getId(), DomainTypes.HoldStatus.ACTIVE)).isPresent();
        assertThat(seats.findById(seat.getId()).orElseThrow().getStatus()).isEqualTo(DomainTypes.SeatStatus.HELD);
    }

    @Test
    void repeatedPaymentWithTheSameKeyReturnsOneCapture() {
        ApiModels.HoldResponse hold = holdService.create(seat.getId(), buyer.getId());
        ApiModels.OrderResponse order = orderService.create(hold.id(), buyer.getId());
        String key = UUID.randomUUID().toString();

        ApiModels.PaymentResponse first = paymentService.capture(order.id(), key, buyer.getId());
        ApiModels.PaymentResponse replay = paymentService.capture(order.id(), key, buyer.getId());

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(payments.findByIdempotencyKey(key).orElseThrow().getId()).isEqualTo(first.id());
        assertThat(outbox.findAll()).filteredOn(event -> event.getAggregateId().equals(order.id())).hasSize(1);
        assertThat(seats.findById(seat.getId()).orElseThrow().getStatus()).isEqualTo(DomainTypes.SeatStatus.SOLD);
    }

    @Test
    void concurrentPaymentRetriesReturnOneCapture() throws Exception {
        ApiModels.HoldResponse hold = holdService.create(seat.getId(), buyer.getId());
        ApiModels.OrderResponse order = orderService.create(hold.id(), buyer.getId());
        String key = UUID.randomUUID().toString();
        int retries = 25;
        CountDownLatch ready = new CountDownLatch(retries);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<UUID>> results = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(retries)) {
            for (int index = 0; index < retries; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return paymentService.capture(order.id(), key, buyer.getId()).id();
                }));
            }
            ready.await();
            start.countDown();
            assertThat(results.stream().map(this::completed).collect(Collectors.toSet())).hasSize(1);
        }

        assertThat(payments.findByIdempotencyKey(key)).isPresent();
        assertThat(outbox.findAll()).filteredOn(event -> event.getAggregateId().equals(order.id())).hasSize(1);
    }

    @Test
    void expiredHoldReturnsTheSeatToInventory() {
        ApiModels.HoldResponse hold = holdService.create(seat.getId(), buyer.getId());
        jdbc.update("update seat_holds set expires_at = now() - interval '1 second' where id = ?", hold.id());

        assertThat(holdService.expireBatch()).isGreaterThanOrEqualTo(1);

        assertThat(holds.findById(hold.id()).orElseThrow().getStatus()).isEqualTo(DomainTypes.HoldStatus.EXPIRED);
        assertThat(seats.findById(seat.getId()).orElseThrow().getStatus()).isEqualTo(DomainTypes.SeatStatus.AVAILABLE);
    }

    @Test
    void refundReopensTheSeatAndIsIdempotent() {
        ApiModels.HoldResponse hold = holdService.create(seat.getId(), buyer.getId());
        ApiModels.OrderResponse order = orderService.create(hold.id(), buyer.getId());
        paymentService.capture(order.id(), UUID.randomUUID().toString(), buyer.getId());

        ApiModels.PaymentResponse first = paymentService.refund(order.id(), buyer.getId(), false);
        ApiModels.PaymentResponse replay = paymentService.refund(order.id(), buyer.getId(), false);

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(replay.status()).isEqualTo(DomainTypes.PaymentStatus.REFUNDED);
        assertThat(seats.findById(seat.getId()).orElseThrow().getStatus()).isEqualTo(DomainTypes.SeatStatus.AVAILABLE);
    }

    private UUID completed(Future<UUID> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError("Concurrent payment retry failed", exception);
        }
    }

    private String uniqueEmail(String prefix) {
        return prefix + "+" + UUID.randomUUID() + "@example.com";
    }
}
