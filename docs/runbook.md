# Operations Runbook

## Service checks

1. Check `GET /actuator/health` on the API and notification worker.
2. Check PostgreSQL connection saturation and long-running transactions.
3. Check Redis availability and cache error rate.
4. Check RabbitMQ queue depth, unacknowledged messages, and dead letters.
5. Check the oldest unpublished outbox event.
6. Correlate structured logs with trace identifiers.

Do not repair booking state by editing Redis. PostgreSQL is the source of truth.

## Seat appears permanently held

1. Query the active hold and compare `expires_at` with database time.
2. Confirm the expiry scheduler is running on at least one API instance.
3. Check for blocked PostgreSQL transactions on `seat_holds` or `seats`.
4. Run the expiry query in a transaction only after identifying the blocking transaction.
5. Invalidate the session availability cache after a database repair.

Never mark a seat available while a confirmed order references it.

## Outbox backlog grows

1. Check RabbitMQ reachability and credentials.
2. Inspect `outbox_events` where `published_at IS NULL`, ordered by `created_at`.
3. Review publish-failure metrics and logs.
4. Restore the broker or publisher and allow automatic replay.
5. Verify the backlog age returns below 60 seconds.

Do not delete unpublished rows. They are the recovery record for completed payments.

## Dead-letter queue contains messages

1. Inspect the message identifier, event type, retry count, and redacted payload.
2. Confirm the corresponding order and notification-delivery record.
3. Correct the consumer or downstream dependency.
4. Replay one message and verify deduplication.
5. Replay the remaining batch gradually while monitoring error rate.

## High booking-conflict rate

A high conflict rate can be expected for a popular final seat. Compare conflicts with successful holds and latency. Investigate when conflicts occur across different seats, when lock waits rise, or when unique-constraint errors bypass the normal conflict response.

Mitigations include reducing transaction work, verifying relevant indexes, bounding connection pools, and isolating hot sessions at the request-routing layer. Never weaken the database constraint to improve apparent success rate.

## Database failover

1. Stop deployment changes.
2. Confirm the managed database has promoted a healthy writer.
3. Allow connection pools to reconnect and check migrations did not rerun incorrectly.
4. Verify hold, order, payment, and outbox counts are internally consistent.
5. Run a controlled hold and idempotent payment smoke test.
6. Resume traffic gradually.

## Rollback

Application images are immutable. Roll back the ECS task definition or container tag. Database migrations are forward-only; use a corrective migration rather than editing or deleting an applied Flyway migration. Confirm the older application remains compatible with the current schema before rollback.

## Backup and recovery

- Enable encrypted automated PostgreSQL backups and point-in-time recovery.
- Test restoration in an isolated account or VPC at least quarterly.
- Persist RabbitMQ definitions and rely on the outbox for message recovery.
- Treat Redis as disposable; recovery must not require a Redis snapshot.
- Retain audit logs according to business and regulatory policy.

## Incident record

Create a report from [the postmortem template](postmortem-template.md) for every severity-one incident and every correctness violation, including near misses.
