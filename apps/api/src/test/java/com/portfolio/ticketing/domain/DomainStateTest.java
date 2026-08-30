package com.portfolio.ticketing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainStateTest {

    @Test
    void seatCanOnlyBeSoldFromHeldState() {
        SeatEntity seat = seat();

        assertThatThrownBy(seat::sell)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only a held seat can be sold");

        seat.hold();
        seat.sell();

        assertThat(seat.getStatus()).isEqualTo(DomainTypes.SeatStatus.SOLD);
    }

    @Test
    void holdReleaseIsIdempotent() {
        UserAccount user = new UserAccount("buyer@example.com", "hash", DomainTypes.Role.BUYER);
        SeatHold hold = new SeatHold(seat(), user, Instant.now().plusSeconds(300));

        hold.release();
        hold.release();

        assertThat(hold.getStatus()).isEqualTo(DomainTypes.HoldStatus.RELEASED);
    }

    @Test
    void refundedSeatCanBeReopened() {
        SeatEntity seat = seat();
        seat.hold();
        seat.sell();

        seat.reopenAfterRefund();

        assertThat(seat.getStatus()).isEqualTo(DomainTypes.SeatStatus.AVAILABLE);
    }

    private SeatEntity seat() {
        UserAccount organizer = new UserAccount("organizer@example.com", "hash", DomainTypes.Role.ORGANIZER);
        EventEntity event = new EventEntity(organizer, "Event", "Description");
        EventSessionEntity session = new EventSessionEntity(event, Instant.now().plusSeconds(3600), "Venue");
        return new SeatEntity(session, "A-01", new BigDecimal("49.00"));
    }
}
