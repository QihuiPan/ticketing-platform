package com.portfolio.ticketing.api;

import com.portfolio.ticketing.service.CurrentUserService;
import com.portfolio.ticketing.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class PaymentController {

    private final PaymentService payments;
    private final CurrentUserService currentUser;

    public PaymentController(PaymentService payments, CurrentUserService currentUser) {
        this.payments = payments;
        this.currentUser = currentUser;
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiModels.PaymentResponse capture(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ApiModels.PaymentRequest request) {
        return payments.capture(request.orderId(), idempotencyKey, currentUser.id());
    }

    @PostMapping("/orders/{orderId}/refund")
    public ApiModels.PaymentResponse refund(@PathVariable UUID orderId) {
        return payments.refund(orderId, currentUser.id(), currentUser.isAdmin());
    }
}
