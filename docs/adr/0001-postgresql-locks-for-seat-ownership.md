# ADR 0001: Use PostgreSQL Locks for Seat Ownership

- Status: Accepted
- Date: 2026-08-31

## Context

Many buyers can request the same seat at the same time. Correctness requires a single winner even when API instances, Redis, or clients fail.

## Decision

Serialize contenders with a PostgreSQL row lock on the seat and enforce one active hold with a partial unique index. PostgreSQL timestamps determine hold validity. Redis is not part of the ownership decision.

## Consequences

The invariant is visible, testable, and protected by the same transaction that writes booking state. Hot seats serialize at the database, which is the required behavior. Transactions must remain short, connection pools must be bounded, and lock latency must be monitored.
