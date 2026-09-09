# Hexa — Backend

Backend for **Hexa**, a real-time multiplayer game where players are shown a target color and must photograph a real-world object that matches it. Built with Spring Boot, PostgreSQL, and Server-Sent Events for live state sync.

## How it works

- Each match happens in a **Room** (up to 8 players), created and joined via a short room code.
- Every player has a 3x3 grid of 9 photo slots, and **each slot has its own target color** — there's no single target per round.
- Players snap a photo per slot; the backend scores it by comparing the captured color against that slot's target and uploads the image to Cloudinary.
- Room state (players, photos, scores) is pushed live to every connected client over **Server-Sent Events** — no WebSockets, no polling.

## Tech stack

- **Java 21**, Spring Boot, Maven (wrapper included, no local Maven install needed)
- **PostgreSQL** via Spring Data JPA / Hibernate
- **Cloudinary** for image storage (no binaries touch the backend's disk or database)
- **SSE** (`SseEmitter`) for real-time room updates
- Cookie-based session auth (`HttpOnly`, `Secure`, `SameSite=None`) — no JWTs, no traditional login

## Architecture at a glance

```
Client (React + Vite)
   │  REST (create/join/photo/ready)         ▲  SSE (GAME_STATE, PLAYER_JOINED, ...)
   ▼                                          │
RoomController ──▶ RoomService ──▶ PostgreSQL │
   │                                          │
   ├──▶ Cloudinary (photo upload)             │
   └──▶ RoomSseService.broadcastToRoom() ─────┘
```

- **Mutations are REST, sync is SSE.** A client calls a normal POST/PATCH endpoint; the backend applies the change, then separately broadcasts the new state to every subscriber of that room.
- **DTOs only.** No endpoint returns a JPA entity directly — everything goes through `RoomStateResponse` / `PlayerDto` / `PhotoDto`.
- **Session validation on every mutation.** Any endpoint that changes a specific player's state validates the session cookie against that `playerId` via `SessionTokenService` before touching data.
- A scheduled cleanup job removes rooms (and their Cloudinary images) older than 3 hours.

See [ARCHITECTURE.md](ARCHITECTURE.md) and [API_DOCS.md](API_DOCS.md) for full detail (real-time events, media pipeline, error handling, endpoint-by-endpoint reference).

## Getting started

Requires **JDK 21** and a local **PostgreSQL** instance.

1. Create a database named `hex_database`.
2. Provide credentials — there are no defaults for secrets, the app **will not start** without them. Either:
   - Copy `env.example` to `.env` and fill in `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, `SESSION_SECRET`, or
   - Create `src/main/resources/application-local.yml` with the same values and run with the `local` profile.
3. Run it:
   ```bash
   ./mvnw spring-boot:run
   ```
   The API listens on port `8080` by default (`PORT` env var overrides it).

Full contributor setup (frontend included) and code conventions live in [CONTRIBUTING.md](CONTRIBUTING.md).

## Configuration

All configuration is environment-variable driven (see `application.yml`):

| Variable | Purpose | Default |
|---|---|---|
| `DB_URL` | JDBC URL for PostgreSQL | `jdbc:postgresql://localhost:5432/hex_database` |
| `DB_USERNAME` / `DB_PASSWORD` | Database credentials | `admin` / `admin` |
| `CLOUDINARY_CLOUD_NAME` / `CLOUDINARY_API_KEY` / `CLOUDINARY_API_SECRET` | Cloudinary credentials for photo storage | — (required) |
| `SESSION_SECRET` | Signs player session tokens | dev-only default — **must** be overridden in any shared/public deployment |
| `FRONTEND_URL` | Allowed CORS origin for the deployed frontend | `http://localhost:5174` |
| `PORT` | HTTP port | `8080` |

## Deployment

Ships as a Docker image (see `Dockerfile`, multi-stage Maven build → JRE runtime) and is deployed to **Render** as a web service, with the frontend on Vercel. Render injects `PORT` automatically; set the rest of the table above as environment variables on the service. Health check: `GET /actuator/health`.

## Docs

- [API_DOCS.md](API_DOCS.md) — endpoint reference, request/response shapes, error codes
- [ARCHITECTURE.md](ARCHITECTURE.md) — real-time flow, media pipeline, offline resilience
- [CONTRIBUTING.md](CONTRIBUTING.md) — local setup, branching, code rules
