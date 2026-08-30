package com.portfolio.ticketing.service;

import com.portfolio.ticketing.api.ApiException;
import com.portfolio.ticketing.api.ApiModels;
import com.portfolio.ticketing.domain.DomainTypes;
import com.portfolio.ticketing.domain.EventEntity;
import com.portfolio.ticketing.domain.EventSessionEntity;
import com.portfolio.ticketing.domain.SeatEntity;
import com.portfolio.ticketing.domain.UserAccount;
import com.portfolio.ticketing.repository.EventRepository;
import com.portfolio.ticketing.repository.EventSessionRepository;
import com.portfolio.ticketing.repository.SeatRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CatalogService {

    private final EventRepository events;
    private final EventSessionRepository sessions;
    private final SeatRepository seats;
    private final CurrentUserService currentUser;
    private final AuditService audit;

    public CatalogService(
            EventRepository events,
            EventSessionRepository sessions,
            SeatRepository seats,
            CurrentUserService currentUser,
            AuditService audit) {
        this.events = events;
        this.sessions = sessions;
        this.seats = seats;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<ApiModels.EventResponse> search(String query, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return events.findByStatusAndTitleContainingIgnoreCase(
                        DomainTypes.EventStatus.PUBLISHED,
                        query == null ? "" : query.trim(),
                        pageable)
                .map(this::eventResponse);
    }

    @Transactional
    public ApiModels.EventResponse createEvent(ApiModels.CreateEventRequest request) {
        UserAccount organizer = currentUser.account();
        EventEntity event = events.save(new EventEntity(
                organizer, request.title().trim(), request.description().trim()));
        audit.record(organizer, "EVENT_CREATED", "Event", event.getId(), null,
                "{\"status\":\"DRAFT\"}");
        return eventResponse(event);
    }

    @Transactional
    public ApiModels.EventResponse publish(UUID eventId) {
        EventEntity event = ownedEvent(eventId);
        event.publish();
        audit.record(currentUser.account(), "EVENT_PUBLISHED", "Event", eventId,
                "{\"status\":\"DRAFT\"}", "{\"status\":\"PUBLISHED\"}");
        return eventResponse(event);
    }

    @Transactional
    public ApiModels.SessionResponse createSession(UUID eventId, ApiModels.CreateSessionRequest request) {
        EventEntity event = ownedEvent(eventId);
        EventSessionEntity session = sessions.save(new EventSessionEntity(
                event, request.startsAt(), request.venue().trim()));
        List<SeatEntity> newSeats = request.seats().stream()
                .map(definition -> new SeatEntity(session, definition.label().trim(), definition.price()))
                .toList();
        seats.saveAll(newSeats);
        audit.record(currentUser.account(), "SESSION_CREATED", "EventSession", session.getId(), null,
                "{\"seatCount\":" + newSeats.size() + "}");
        return sessionResponse(session);
    }

    @Transactional(readOnly = true)
    public List<ApiModels.SessionResponse> sessions(UUID eventId) {
        EventEntity event = events.findById(eventId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event was not found"));
        if (event.getStatus() != DomainTypes.EventStatus.PUBLISHED) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event was not found");
        }
        return sessions.findByEventIdOrderByStartsAt(eventId).stream()
                .map(this::sessionResponse)
                .toList();
    }

    private EventEntity ownedEvent(UUID eventId) {
        EventEntity event = events.findById(eventId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event was not found"));
        UserAccount user = currentUser.account();
        if (!event.getOrganizer().getId().equals(user.getId()) && !currentUser.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EVENT_ACCESS_DENIED", "You do not manage this event");
        }
        return event;
    }

    private ApiModels.EventResponse eventResponse(EventEntity event) {
        return new ApiModels.EventResponse(
                event.getId(), event.getTitle(), event.getDescription(), event.getStatus(), event.getCreatedAt());
    }

    private ApiModels.SessionResponse sessionResponse(EventSessionEntity session) {
        return new ApiModels.SessionResponse(
                session.getId(), session.getEvent().getId(), session.getStartsAt(), session.getVenue());
    }
}
