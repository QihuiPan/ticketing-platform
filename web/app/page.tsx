"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

type EventItem = {
  id: string;
  title: string;
  description: string;
  status: string;
};

type EventSession = {
  id: string;
  startsAt: string;
  venue: string;
};

type Seat = {
  id: string;
  label: string;
  price: number;
  status: "AVAILABLE" | "HELD" | "SOLD";
};

type Hold = {
  id: string;
  seatId: string;
  expiresAt: string;
  remainingSeconds: number;
};

type Order = {
  id: string;
  amount: number;
  status: string;
  ticketCode?: string;
};

type ApiError = {
  message?: string;
};

async function readJson<T>(response: Response): Promise<T> {
  const body = (await response.json()) as T & ApiError;
  if (!response.ok) {
    throw new Error(body.message ?? `Request failed with status ${response.status}`);
  }
  return body;
}

export default function Home() {
  const [email, setEmail] = useState("buyer@example.com");
  const [password, setPassword] = useState("DemoBuyer123!");
  const [token, setToken] = useState("");
  const [events, setEvents] = useState<EventItem[]>([]);
  const [selectedEvent, setSelectedEvent] = useState<EventItem>();
  const [sessions, setSessions] = useState<EventSession[]>([]);
  const [selectedSession, setSelectedSession] = useState<EventSession>();
  const [seats, setSeats] = useState<Seat[]>([]);
  const [hold, setHold] = useState<Hold>();
  const [order, setOrder] = useState<Order>();
  const [idempotencyKey, setIdempotencyKey] = useState("");
  const [notice, setNotice] = useState("Loading live inventory…");
  const [busy, setBusy] = useState(false);

  const authHeaders = useMemo<Record<string, string>>(() => {
    const headers: Record<string, string> = {};
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
    return headers;
  }, [token]);

  const loadEvents = useCallback(async () => {
    try {
      const response = await fetch(`${API_URL}/api/events?size=12`, { cache: "no-store" });
      const page = await readJson<{ content: EventItem[] }>(response);
      setEvents(page.content);
      setNotice(page.content.length ? "Choose an event to inspect its live seat inventory." : "No events are published yet.");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "The event catalog is unavailable.");
    }
  }, []);

  useEffect(() => {
    // Initial catalog hydration is intentionally triggered once after the client mounts.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadEvents();
  }, [loadEvents]);

  async function login(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      const response = await fetch(`${API_URL}/api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      });
      const body = await readJson<{ accessToken: string }>(response);
      setToken(body.accessToken);
      setNotice("Signed in. You can now reserve an available seat.");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "Sign-in failed.");
    } finally {
      setBusy(false);
    }
  }

  async function chooseEvent(item: EventItem) {
    setSelectedEvent(item);
    setSelectedSession(undefined);
    setSeats([]);
    setHold(undefined);
    setOrder(undefined);
    setBusy(true);
    try {
      const response = await fetch(`${API_URL}/api/events/${item.id}/sessions`, { cache: "no-store" });
      const body = await readJson<EventSession[]>(response);
      setSessions(body);
      setNotice(body.length ? "Select a performance." : "This event has no scheduled performances.");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "Sessions are unavailable.");
    } finally {
      setBusy(false);
    }
  }

  async function chooseSession(item: EventSession) {
    setSelectedSession(item);
    setBusy(true);
    try {
      const response = await fetch(`${API_URL}/api/sessions/${item.id}/availability`, { cache: "no-store" });
      const body = await readJson<{ seats: Seat[] }>(response);
      setSeats(body.seats);
      setNotice("Inventory is live. Database locking decides the winner when requests race.");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "Seat inventory is unavailable.");
    } finally {
      setBusy(false);
    }
  }

  async function reserve(seat: Seat) {
    if (!token) {
      setNotice("Sign in before reserving a seat.");
      return;
    }
    setBusy(true);
    try {
      const response = await fetch(`${API_URL}/api/holds`, {
        method: "POST",
        headers: { ...authHeaders, "Content-Type": "application/json" },
        body: JSON.stringify({ seatId: seat.id }),
      });
      const body = await readJson<Hold>(response);
      setHold(body);
      setNotice(`Seat ${seat.label} is held for ${body.remainingSeconds} seconds.`);
      if (selectedSession) await chooseSession(selectedSession);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "The seat could not be reserved.");
    } finally {
      setBusy(false);
    }
  }

  async function createOrder() {
    if (!hold) return;
    setBusy(true);
    try {
      const response = await fetch(`${API_URL}/api/orders`, {
        method: "POST",
        headers: { ...authHeaders, "Content-Type": "application/json" },
        body: JSON.stringify({ holdId: hold.id }),
      });
      const body = await readJson<Order>(response);
      setOrder(body);
      setIdempotencyKey(crypto.randomUUID());
      setNotice("Order created. Repeated payment requests will return the same result.");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "The order could not be created.");
    } finally {
      setBusy(false);
    }
  }

  async function pay() {
    if (!order || !idempotencyKey) return;
    setBusy(true);
    try {
      const response = await fetch(`${API_URL}/api/payments`, {
        method: "POST",
        headers: {
          ...authHeaders,
          "Content-Type": "application/json",
          "Idempotency-Key": idempotencyKey,
        },
        body: JSON.stringify({ orderId: order.id }),
      });
      await readJson(response);
      const ordersResponse = await fetch(`${API_URL}/api/me/orders`, { headers: authHeaders });
      const mine = await readJson<Order[]>(ordersResponse);
      setOrder(mine.find((item) => item.id === order.id) ?? order);
      setNotice("Payment captured once. The electronic ticket is ready.");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "Payment failed.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main>
      <header className="topbar">
        <a className="brand" href="#top" aria-label="SeatForge home">
          <span className="brandMark">SF</span>
          <span>SeatForge</span>
        </a>
        <span className="systemStatus"><i /> PostgreSQL is the source of truth</span>
      </header>

      <section className="hero" id="top">
        <div className="eyebrow">Concurrency-safe ticketing</div>
        <h1>One seat. One winner.<br /><em>Zero overselling.</em></h1>
        <p>
          A production-oriented reservation flow built around transactional locks,
          idempotent payments, and reliable event delivery.
        </p>
        <div className="signalRow">
          <span>Row-level locks</span><span>5-minute holds</span><span>Transactional outbox</span>
        </div>
      </section>

      <section className="workspace">
        <aside className="loginCard">
          <div className="stepLabel">01 / Access</div>
          <h2>Demo account</h2>
          <p>Use the seeded buyer to complete the reservation journey.</p>
          <form onSubmit={login}>
            <label>Email<input value={email} onChange={(event) => setEmail(event.target.value)} type="email" /></label>
            <label>Password<input value={password} onChange={(event) => setPassword(event.target.value)} type="password" /></label>
            <button disabled={busy || Boolean(token)}>{token ? "Signed in" : "Sign in"}</button>
          </form>
          <div className="notice" role="status">{notice}</div>
        </aside>

        <div className="inventory">
          <div className="sectionHeading">
            <div><div className="stepLabel">02 / Inventory</div><h2>Published events</h2></div>
            <button className="ghost" onClick={() => void loadEvents()} disabled={busy}>Refresh</button>
          </div>
          <div className="eventGrid">
            {events.map((item) => (
              <button className={`eventCard ${selectedEvent?.id === item.id ? "active" : ""}`} key={item.id} onClick={() => void chooseEvent(item)}>
                <span className="eventType">Live event</span>
                <strong>{item.title}</strong>
                <small>{item.description}</small>
              </button>
            ))}
          </div>

          {selectedEvent && (
            <div className="sessionStrip">
              {sessions.map((item) => (
                <button key={item.id} className={selectedSession?.id === item.id ? "active" : ""} onClick={() => void chooseSession(item)}>
                  <strong>{new Date(item.startsAt).toLocaleString()}</strong>
                  <span>{item.venue}</span>
                </button>
              ))}
            </div>
          )}

          {selectedSession && (
            <div className="seatPanel">
              <div className="legend"><span><i className="available" />Available</span><span><i className="held" />Held</span><span><i className="sold" />Sold</span></div>
              <div className="stage">STAGE</div>
              <div className="seatGrid">
                {seats.map((seat) => (
                  <button
                    key={seat.id}
                    className={`seat ${seat.status.toLowerCase()}`}
                    disabled={busy || seat.status !== "AVAILABLE"}
                    onClick={() => void reserve(seat)}
                    aria-label={`${seat.label}, ${seat.status.toLowerCase()}, $${Number(seat.price).toFixed(2)}`}
                  >
                    <span>{seat.label}</span><small>${Number(seat.price).toFixed(0)}</small>
                  </button>
                ))}
              </div>
            </div>
          )}

          {hold && (
            <div className="checkout">
              <div><span>Active hold</span><strong>{hold.id.slice(0, 8)}</strong><small>Expires {new Date(hold.expiresAt).toLocaleTimeString()}</small></div>
              {!order && <button onClick={() => void createOrder()} disabled={busy}>Create order</button>}
              {order?.status === "PENDING" && <button onClick={() => void pay()} disabled={busy}>Pay ${Number(order.amount).toFixed(2)}</button>}
              {order?.status === "CONFIRMED" && (
                <a className="button" href={`${API_URL}/api/orders/${order.id}/ticket`} target="_blank" rel="noreferrer">Open ticket QR</a>
              )}
            </div>
          )}
        </div>
      </section>

      <footer>
        <span>SeatForge engineering demo</span>
        <span>Locks · Idempotency · Outbox · Observability</span>
      </footer>
    </main>
  );
}
