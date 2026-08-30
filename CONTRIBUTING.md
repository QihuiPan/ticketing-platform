# Contributing

## Workflow

1. Create a focused branch from `main`.
2. Add tests for every behavioral change, especially concurrency and idempotency changes.
3. Update `CHANGELOG.md` in the same pull request.
4. Run `./mvnw verify` and `npm run build` in `web`.
5. Explain schema, API, and operational impact in the pull request description.

## Engineering rules

- Keep PostgreSQL as the inventory source of truth.
- Preserve idempotency across retries and at-least-once message delivery.
- Do not add a distributed lock without an architecture decision record.
- Use structured logs and propagate correlation identifiers.
- Keep code comments, API labels, documentation, and release notes in English.
