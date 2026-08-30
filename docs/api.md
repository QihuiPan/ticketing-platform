# API Reference

The default API origin is `http://localhost:8080`. JSON endpoints return an error object with `code`, `message`, and `timestamp` on failure. Protected endpoints require `Authorization: Bearer <token>`.

## Authentication

| Method | Path | Access | Description |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | Public | Register a buyer |
| POST | `/api/auth/login` | Public | Issue a JWT |

```json
{
  "email": "buyer@example.com",
  "password": "DemoBuyer123!"
}
```

## Catalog and inventory

| Method | Path | Access | Description |
| --- | --- | --- | --- |
| GET | `/api/events?query=&page=0&size=20` | Public | Search published events |
| POST | `/api/events` | Organizer or admin | Create a draft event |
| POST | `/api/events/{eventId}/publish` | Organizer or admin | Publish an event |
| POST | `/api/events/{eventId}/sessions` | Organizer or admin | Create a session and its seats |
| GET | `/api/events/{eventId}/sessions` | Public | List event sessions |
| GET | `/api/sessions/{sessionId}/availability` | Public | Read live seat availability |

Create a session:

```json
{
  "startsAt": "2026-10-01T12:00:00Z",
  "venue": "Harbor Arena",
  "seats": [
    { "label": "A-1", "price": 120.00 },
    { "label": "A-2", "price": 120.00 }
  ]
}
```

## Holds and orders

| Method | Path | Access | Description |
| --- | --- | --- | --- |
| POST | `/api/holds` | Buyer | Hold one seat for five minutes |
| GET | `/api/holds/{holdId}` | Owner | Read hold status and remaining time |
| DELETE | `/api/holds/{holdId}` | Owner | Release an active hold |
| POST | `/api/orders` | Buyer | Create one order for an active hold |
| GET | `/api/me/orders` | Buyer | List the current user's orders |

Create a hold:

```json
{
  "seatId": "00000000-0000-0000-0000-000000000000"
}
```

Create an order:

```json
{
  "holdId": "00000000-0000-0000-0000-000000000000"
}
```

## Payments, refunds, and tickets

| Method | Path | Access | Description |
| --- | --- | --- | --- |
| POST | `/api/payments` | Order owner | Capture payment exactly once |
| POST | `/api/orders/{orderId}/refund` | Owner or admin | Refund a confirmed order |
| GET | `/api/orders/{orderId}/ticket` | Owner | Download the ticket QR image |

Payment requests require a stable idempotency key:

```http
POST /api/payments HTTP/1.1
Authorization: Bearer <token>
Idempotency-Key: checkout-8ccff52d-9a68-4a53-93ad-8db0e41178f3
Content-Type: application/json

{
  "orderId": "00000000-0000-0000-0000-000000000000"
}
```

Replaying the same key returns the original payment. Reusing a key for a different order is rejected.

## Status codes

| Status | Meaning |
| --- | --- |
| 200 | Read, idempotent replay, release, or refund succeeded |
| 201 | Hold, order, or payment created |
| 400 | Validation failed or request state is invalid |
| 401 | Authentication is missing or invalid |
| 403 | The authenticated user lacks permission |
| 404 | The requested resource does not exist |
| 409 | Seat, hold, order, or idempotency state conflicts |
| 429 | The write-rate limit was exceeded |
