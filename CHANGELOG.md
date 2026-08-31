# Changelog

All notable changes to this project are documented in this file. Every pull request must update either the `Unreleased` section or a versioned entry.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses semantic versioning.

## [Unreleased]

### Added

- Added a cost-controlled single-node AWS portfolio demo with EC2, encrypted storage, Systems Manager administration, a private short-lived deployment bucket, Caddy routing, and a memory-bounded Docker Compose stack.
- Added AWS Budget notifications at 50 percent forecasted spend and 80/100 percent actual spend, plus a reproducible PowerShell deployment workflow for committed Git revisions.
- Added configurable demonstration credentials and documented their security boundary so public portfolio deployments can retain the shared buyer flow while randomizing the privileged organizer password at first boot.
- Added the complete English project guide, API reference, architecture guide, operations runbook, incident template, architecture decisions, pull request checklist, and MIT license.
- Added tagged release automation that publishes versioned API, worker, and web images to GitHub Container Registry.
- Expanded PostgreSQL integration coverage for 100-way seat contention, concurrent payment retries, automatic hold expiry, and idempotent refunds.
- Added scoped Docker build contexts that exclude local dependencies, build products, secrets, and Terraform state.

### Changed

- Added configurable RabbitMQ TLS for the encrypted Amazon MQ production path.
- Hardened cache invalidation with post-commit callbacks, added global payment-key serialization, required broker publisher confirms, validated JWT issuers, restricted booking to published future sessions, reopened refunded seats, and enabled TLS for managed Redis and PostgreSQL.
- Pinned the Maven wrapper distribution and checksum for reproducible builds.
- Reduced cold container build time by resolving only dependencies required by each service package.
- Normalized repository text files to a single terminal newline for clean cross-platform Git checks.
- Upgraded GitHub-owned workflow actions to their current Node.js 24-based major releases.

## [0.3.0] - 2026-08-31

### Added

- Added the Next.js operator demo, Prometheus and OpenTelemetry configuration, k6 contention scenario, CI pipeline, AWS infrastructure baseline, runbook, security guidance, and architecture decisions.

## [0.2.0] - 2026-08-31

### Added

- Added Redis-backed availability caching and request throttling with safe database fallbacks.
- Added the transactional outbox publisher, RabbitMQ topology, deduplicating notification worker, retry handling, and dead-letter queue.
- Added idempotent payment capture, refunds, electronic ticket QR generation, audit records, and structured metrics.

## [0.1.0] - 2026-08-31

### Added

- Added the Spring Boot modular monolith, PostgreSQL schema, JWT authentication, RBAC, event catalog, seat inventory, concurrency-safe holds, automatic hold expiry, orders, and initial tests.
