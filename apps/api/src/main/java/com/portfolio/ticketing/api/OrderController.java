package com.portfolio.ticketing.api;

import com.portfolio.ticketing.service.CurrentUserService;
import com.portfolio.ticketing.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderService orders;
    private final CurrentUserService currentUser;

    public OrderController(OrderService orders, CurrentUserService currentUser) {
        this.orders = orders;
        this.currentUser = currentUser;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiModels.OrderResponse create(@Valid @RequestBody ApiModels.CreateOrderRequest request) {
        return orders.create(request.holdId(), currentUser.id());
    }

    @GetMapping("/me/orders")
    public List<ApiModels.OrderResponse> mine() {
        return orders.forUser(currentUser.id());
    }
}
