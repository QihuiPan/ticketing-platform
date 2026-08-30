package com.portfolio.ticketing.api;

import com.portfolio.ticketing.service.CurrentUserService;
import com.portfolio.ticketing.service.HoldService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/holds")
public class HoldController {

    private final HoldService holds;
    private final CurrentUserService currentUser;

    public HoldController(HoldService holds, CurrentUserService currentUser) {
        this.holds = holds;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiModels.HoldResponse create(@Valid @RequestBody ApiModels.CreateHoldRequest request) {
        return holds.create(request.seatId(), currentUser.id());
    }

    @GetMapping("/{holdId}")
    public ApiModels.HoldResponse get(@PathVariable UUID holdId) {
        return holds.get(holdId, currentUser.id());
    }

    @DeleteMapping("/{holdId}")
    public ApiModels.HoldResponse release(@PathVariable UUID holdId) {
        return holds.release(holdId, currentUser.id());
    }
}
