CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE events (
    id UUID PRIMARY KEY,
    organizer_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_status_created ON events(status, created_at DESC);

CREATE TABLE event_sessions (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    starts_at TIMESTAMPTZ NOT NULL,
    venue VARCHAR(200) NOT NULL
);

CREATE INDEX idx_sessions_event_start ON event_sessions(event_id, starts_at);

CREATE TABLE seats (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES event_sessions(id) ON DELETE CASCADE,
    label VARCHAR(64) NOT NULL,
    price NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_seat_session_label UNIQUE(session_id, label)
);

CREATE INDEX idx_seats_session_status ON seats(session_id, status);

CREATE TABLE seat_holds (
    id UUID PRIMARY KEY,
    seat_id UUID NOT NULL REFERENCES seats(id),
    user_id UUID NOT NULL REFERENCES users(id),
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_active_hold_per_seat ON seat_holds(seat_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_holds_expiry ON seat_holds(status, expires_at);
CREATE INDEX idx_holds_user ON seat_holds(user_id, created_at DESC);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    hold_id UUID NOT NULL UNIQUE REFERENCES seat_holds(id),
    amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    status VARCHAR(32) NOT NULL,
    ticket_code VARCHAR(128) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_user_created ON orders(user_id, created_at DESC);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    status VARCHAR(32) NOT NULL,
    captured_at TIMESTAMPTZ,
    refunded_at TIMESTAMPTZ
);

CREATE INDEX idx_payments_order ON payments(order_id);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_unpublished ON outbox_events(created_at) WHERE published_at IS NULL;

CREATE TABLE notification_deliveries (
    event_id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    recipient VARCHAR(320) NOT NULL,
    status VARCHAR(32) NOT NULL,
    delivered_at TIMESTAMPTZ,
    last_error VARCHAR(1000)
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor_id UUID REFERENCES users(id),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID NOT NULL,
    before_state JSONB,
    after_state JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id, created_at DESC);
