# Security Policy

## Reporting

Do not open a public issue for a suspected vulnerability. Send a private GitHub security advisory with reproduction steps, affected endpoints, and the observed impact.

## Baseline controls

- Passwords are stored with BCrypt and are never logged.
- API access tokens are short-lived HMAC-signed JWTs. Production deployments must load the secret from a managed secret store.
- Object ownership is checked in service methods to prevent insecure direct object reference attacks.
- Administrative actions create append-only audit records.
- Rate limiting protects mutation endpoints and degrades open if Redis is unavailable so cache failure cannot corrupt inventory correctness.
- PostgreSQL remains the source of truth for seats, holds, orders, and payments.

## Production checklist

- Rotate all sample credentials before deployment.
- Use TLS at the load balancer and require encrypted database and broker connections.
- Restrict database, Redis, and RabbitMQ security groups to application subnets.
- Enable dependency scanning, container scanning, and secret scanning in GitHub.
- Review audit logs and authentication failures through the centralized logging system.

## Portfolio demo boundary

- The AWS single-node demo intentionally exposes the shared buyer credential so reviewers can exercise simulated booking flows.
- The EC2 bootstrap randomizes the organizer password and stores it only in the root-readable deployment environment file.
- Never load real customer data, payment credentials, or production secrets into the single-node demo.
- Destroy the demo infrastructure when it is no longer being reviewed; it does not provide production-grade availability or backups.
