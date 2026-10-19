# Day 1: Idempotency

A client double-clicks "Pay". Without protection, the account is charged twice.

## The idea

The client sends an `Idempotency-Key` header. The server remembers which payment that key produced.
A duplicate request gets the original response back (marked `Idempotent-Replayed: true`) instead of a second charge.
Reusing a key with a different request is rejected with `422`.

## Two implementations, selected by Spring profile

- `broken`: ignores the key, every request is a new payment.
- `fixed` (default): looks the key up before debiting. A unique constraint on the key is the database-level safety net.

## Run it

1. `docker compose up -d` (from the repository root)
2. Start the app with a profile: `mvn spring-boot:run -Dspring-boot.run.profiles=broken` (or `fixed`)
3. In another terminal: `./scripts/demo.sh`
4. Stop the app, restart with the other profile, run the script again.

Requirements: Docker, Java 21, Maven, `curl`, `jq`.
Set `NO_COLOR=1` to disable colors, `BASE_URL` to target another host.

## Tests

`mvn test` (Docker is required for Testcontainers).

## Limits

- Keys never expire here. A real system needs a retention policy.
- Failed requests are not remembered: a retry after an error is processed again.
- This works because the debit and the key live in the same database. With an external payment provider you need an
  explicit state machine (for example IN_PROGRESS, then COMPLETED) and an idempotent call to the provider as well.