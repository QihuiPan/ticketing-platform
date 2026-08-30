package com.portfolio.ticketing.api;

import com.portfolio.ticketing.service.AvailabilityService;
import com.portfolio.ticketing.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalog;
    private final AvailabilityService availability;

    public CatalogController(CatalogService catalog, AvailabilityService availability) {
        this.catalog = catalog;
        this.availability = availability;
    }

    @GetMapping("/events")
    public Page<ApiModels.EventResponse> search(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return catalog.search(query, page, size);
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ApiModels.EventResponse createEvent(@Valid @RequestBody ApiModels.CreateEventRequest request) {
        return catalog.createEvent(request);
    }

    @PostMapping("/events/{eventId}/publish")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ApiModels.EventResponse publish(@PathVariable UUID eventId) {
        return catalog.publish(eventId);
    }

    @PostMapping("/events/{eventId}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ApiModels.SessionResponse createSession(
            @PathVariable UUID eventId,
            @Valid @RequestBody ApiModels.CreateSessionRequest request) {
        return catalog.createSession(eventId, request);
    }

    @GetMapping("/events/{eventId}/sessions")
    public List<ApiModels.SessionResponse> sessions(@PathVariable UUID eventId) {
        return catalog.sessions(eventId);
    }

    @GetMapping("/sessions/{sessionId}/availability")
    public ApiModels.AvailabilityResponse availability(@PathVariable UUID sessionId) {
        return availability.get(sessionId);
    }
}
