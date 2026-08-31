package com.portfolio.ticketing.config;

import com.portfolio.ticketing.domain.DomainTypes;
import com.portfolio.ticketing.domain.EventEntity;
import com.portfolio.ticketing.domain.EventSessionEntity;
import com.portfolio.ticketing.domain.SeatEntity;
import com.portfolio.ticketing.domain.UserAccount;
import com.portfolio.ticketing.repository.EventRepository;
import com.portfolio.ticketing.repository.EventSessionRepository;
import com.portfolio.ticketing.repository.SeatRepository;
import com.portfolio.ticketing.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;

@Component
@ConditionalOnProperty(name = "app.seed-demo", havingValue = "true")
public class DemoDataConfig implements ApplicationRunner {

    private final UserAccountRepository users;
    private final EventRepository events;
    private final EventSessionRepository sessions;
    private final SeatRepository seats;
    private final PasswordEncoder passwordEncoder;
    private final String buyerPassword;
    private final String organizerPassword;

    public DemoDataConfig(
            UserAccountRepository users,
            EventRepository events,
            EventSessionRepository sessions,
            SeatRepository seats,
            PasswordEncoder passwordEncoder,
            @Value("${app.demo.buyer-password}") String buyerPassword,
            @Value("${app.demo.organizer-password}") String organizerPassword) {
        this.users = users;
        this.events = events;
        this.sessions = sessions;
        this.seats = seats;
        this.passwordEncoder = passwordEncoder;
        this.buyerPassword = buyerPassword;
        this.organizerPassword = organizerPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        UserAccount organizer = users.save(new UserAccount(
                "organizer@example.com", passwordEncoder.encode(organizerPassword), DomainTypes.Role.ORGANIZER));
        users.save(new UserAccount(
                "buyer@example.com", passwordEncoder.encode(buyerPassword), DomainTypes.Role.BUYER));

        EventEntity event = new EventEntity(
                organizer,
                "Concurrency Live",
                "A demo event designed to prove that seat inventory never oversells.");
        event.publish();
        events.save(event);
        EventSessionEntity session = sessions.save(new EventSessionEntity(
                event, Instant.now().plus(7, ChronoUnit.DAYS), "Distributed Systems Hall"));
        List<SeatEntity> demoSeats = IntStream.rangeClosed(1, 30)
                .mapToObj(number -> new SeatEntity(
                        session, "A-" + String.format("%02d", number), new BigDecimal("49.00")))
                .toList();
        seats.saveAll(demoSeats);
    }
}
