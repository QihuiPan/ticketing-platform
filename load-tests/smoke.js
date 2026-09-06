import http from "k6/http";
import { check, fail } from "k6";

const baseUrl = __ENV.BASE_URL || "http://localhost:8080";
const buyerPassword = __ENV.DEMO_BUYER_PASSWORD || "DemoBuyer123!";

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    checks: ["rate==1"],
    http_req_failed: ["rate==0"],
  },
};

function requireResponse(response, expectedStatus, name) {
  const passed = check(response, {
    [`${name} returns ${expectedStatus}`]: (result) => result.status === expectedStatus,
  });
  if (!passed) {
    fail(`${name} failed with status ${response.status}: ${response.body}`);
  }
  return response;
}

function jsonHeaders(token, extra = {}) {
  return {
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      ...extra,
    },
  };
}

export default function () {
  const health = requireResponse(http.get(`${baseUrl}/actuator/health`), 200, "API health check");
  check(health, { "API reports UP": (response) => response.json("status") === "UP" });

  const login = requireResponse(http.post(
    `${baseUrl}/api/auth/login`,
    JSON.stringify({ email: "buyer@example.com", password: buyerPassword }),
    { headers: { "Content-Type": "application/json" } },
  ), 200, "buyer login");
  const token = login.json("accessToken");
  if (!token) fail("The login response did not contain an access token.");

  const catalog = requireResponse(http.get(`${baseUrl}/api/events?size=1`), 200, "event catalog");
  const eventId = catalog.json("content.0.id");
  if (!eventId) fail("The demo event is missing. Set SEED_DEMO=true and reset the local data volumes.");

  const sessions = requireResponse(http.get(`${baseUrl}/api/events/${eventId}/sessions`), 200, "session catalog");
  const sessionId = sessions.json("0.id");
  if (!sessionId) fail("The demo event has no session.");

  const availability = requireResponse(
    http.get(`${baseUrl}/api/sessions/${sessionId}/availability`),
    200,
    "seat availability",
  );
  const seat = availability.json("seats").find((item) => item.status === "AVAILABLE");
  if (!seat) fail("The demo session has no available seat.");

  const hold = requireResponse(http.post(
    `${baseUrl}/api/holds`,
    JSON.stringify({ seatId: seat.id }),
    jsonHeaders(token),
  ), 201, "seat hold");

  const order = requireResponse(http.post(
    `${baseUrl}/api/orders`,
    JSON.stringify({ holdId: hold.json("id") }),
    jsonHeaders(token),
  ), 201, "order creation");

  const idempotencyKey = `smoke-${Date.now()}-${Math.random()}`;
  const payment = requireResponse(http.post(
    `${baseUrl}/api/payments`,
    JSON.stringify({ orderId: order.json("id") }),
    jsonHeaders(token, { "Idempotency-Key": idempotencyKey }),
  ), 201, "payment capture");
  const replay = requireResponse(http.post(
    `${baseUrl}/api/payments`,
    JSON.stringify({ orderId: order.json("id") }),
    jsonHeaders(token, { "Idempotency-Key": idempotencyKey }),
  ), 201, "idempotent payment replay");
  check(replay, {
    "payment replay returns the original capture": (response) => response.json("id") === payment.json("id"),
  });

  const orders = requireResponse(
    http.get(`${baseUrl}/api/me/orders`, jsonHeaders(token)),
    200,
    "buyer order history",
  );
  check(orders, {
    "confirmed order appears in history": (response) => response.json().some(
      (item) => item.id === order.json("id") && item.status === "CONFIRMED",
    ),
  });

  const ticket = requireResponse(
    http.get(`${baseUrl}/api/orders/${order.json("id")}/ticket`, jsonHeaders(token)),
    200,
    "ticket download",
  );
  check(ticket, {
    "ticket is a PNG image": (response) => response.headers["Content-Type"]?.includes("image/png"),
  });

  requireResponse(http.post(
    `${baseUrl}/api/orders/${order.json("id")}/refund`,
    null,
    jsonHeaders(token),
  ), 200, "payment refund");
  check(payment, { "payment was captured": (response) => response.json("status") === "CAPTURED" });

  const restored = requireResponse(
    http.get(`${baseUrl}/api/sessions/${sessionId}/availability`),
    200,
    "restored availability",
  );
  check(restored, {
    "refund restores the seat": (response) => response.json("seats").some(
      (item) => item.id === seat.id && item.status === "AVAILABLE",
    ),
  });
}
