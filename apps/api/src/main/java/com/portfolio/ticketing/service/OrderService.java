package com.portfolio.ticketing.service;

import com.portfolio.ticketing.api.ApiException;
import com.portfolio.ticketing.api.ApiModels;
import com.portfolio.ticketing.domain.DomainTypes;
import com.portfolio.ticketing.domain.SeatHold;
import com.portfolio.ticketing.domain.TicketOrder;
import com.portfolio.ticketing.repository.SeatHoldRepository;
import com.portfolio.ticketing.repository.TicketOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final SeatHoldRepository holds;
    private final TicketOrderRepository orders;
    private final Clock clock;

    public OrderService(SeatHoldRepository holds, TicketOrderRepository orders, Clock clock) {
        this.holds = holds;
        this.orders = orders;
        this.clock = clock;
    }

    @Transactional
    public ApiModels.OrderResponse create(UUID holdId, UUID userId) {
        return orders.findByHoldId(holdId)
                .map(existing -> {
                    requireOwner(existing, userId);
                    return response(existing);
                })
                .orElseGet(() -> createNew(holdId, userId));
    }

    @Transactional(readOnly = true)
    public List<ApiModels.OrderResponse> forUser(UUID userId) {
        return orders.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketOrder ownedOrder(UUID orderId, UUID userId, boolean adminAllowed) {
        TicketOrder order = orders.findById(orderId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order was not found"));
        if (!order.getUser().getId().equals(userId) && !adminAllowed) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ORDER_ACCESS_DENIED", "You do not own this order");
        }
        return order;
    }

    public ApiModels.OrderResponse response(TicketOrder order) {
        return new ApiModels.OrderResponse(
                order.getId(), order.getHold().getId(), order.getAmount(), order.getStatus(),
                order.getTicketCode(), order.getCreatedAt());
    }

    private ApiModels.OrderResponse createNew(UUID holdId, UUID userId) {
        SeatHold hold = holds.findByIdForUpdate(holdId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "HOLD_NOT_FOUND", "Seat hold was not found"));
        TicketOrder existing = orders.findByHoldId(holdId).orElse(null);
        if (existing != null) {
            requireOwner(existing, userId);
            return response(existing);
        }
        if (!hold.getUser().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "HOLD_ACCESS_DENIED", "You do not own this seat hold");
        }
        Instant now = clock.instant();
        if (hold.getStatus() != DomainTypes.HoldStatus.ACTIVE || hold.isExpired(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "HOLD_INACTIVE", "Seat hold is no longer active");
        }
        return response(orders.save(new TicketOrder(hold.getUser(), hold)));
    }

    private void requireOwner(TicketOrder order, UUID userId) {
        if (!order.getUser().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ORDER_ACCESS_DENIED", "You do not own this order");
        }
    }
}
