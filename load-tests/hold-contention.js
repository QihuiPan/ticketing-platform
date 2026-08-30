import http from "k6/http";
import { check, sleep } from "k6";
import { Counter } from "k6/metrics";

const baseUrl = __ENV.BASE_URL || "http://localhost:8080";
const successes = new Counter("booking_successes");
const conflicts = new Counter("booking_conflicts");

export const options = {
  scenarios: {
    last_seat_contention: {
      executor: "shared-iterations",
      vus: 100,
      iterations: 100,
      maxDuration: "30s",
    },
  },
  thresholds: {
    booking_successes: ["count==1"],
    booking_conflicts: ["count==99"],
    http_req_duration: ["p(95)<400"],
  },
};

export function setup() {
  const login = http.post(`${baseUrl}/api/auth/login`, JSON.stringify({
    email: "buyer@example.com",
    password: "DemoBuyer123!",
  }), { headers: { "Content-Type": "application/json" } });
  check(login, { "buyer login succeeds": (response) => response.status === 200 });

  const catalog = http.get(`${baseUrl}/api/events?size=1`);
  const eventId = catalog.json("content.0.id");
  const sessions = http.get(`${baseUrl}/api/events/${eventId}/sessions`);
  const sessionId = sessions.json("0.id");
  const availability = http.get(`${baseUrl}/api/sessions/${sessionId}/availability`);
  const seat = availability.json("seats").find((item) => item.status === "AVAILABLE");

  return { token: login.json("accessToken"), seatId: seat.id };
}

export default function (data) {
  const response = http.post(
    `${baseUrl}/api/holds`,
    JSON.stringify({ seatId: data.seatId }),
    {
      headers: {
        Authorization: `Bearer ${data.token}`,
        "Content-Type": "application/json",
      },
      responseCallback: http.expectedStatuses(201, 409),
    },
  );
  if (response.status === 201) successes.add(1);
  if (response.status === 409) conflicts.add(1);
  check(response, { "request has a deterministic outcome": (result) => [201, 409].includes(result.status) });
  sleep(0.05);
}
