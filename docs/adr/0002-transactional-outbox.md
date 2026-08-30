# ADR 0002: Use a Transactional Outbox

- Status: Accepted
- Date: 2026-08-31

## Context

A confirmed payment must eventually trigger a ticket notification. Publishing directly to RabbitMQ inside the database transaction can lose an event or publish an event for a rolled-back payment.

## Decision

Write the domain change and outbox event in one PostgreSQL transaction. Publish committed events asynchronously. Use at-least-once delivery and make the consumer idempotent with a durable event-identifier table.

## Consequences

Payments do not depend on broker latency and broker outages are recoverable. Duplicate delivery is expected and safe. Operators must monitor backlog age, retry failures, and the dead-letter queue.
