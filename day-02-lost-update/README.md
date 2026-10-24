# Day 2: Lost update and locking

Two withdrawals hit the same account at the same time, and one of them disappears.

## The bug

A read-modify-write sends an absolute value computed from a read that may already be stale:
two transactions read 1000, both compute 990, both write 990. Both succeed, one debit is lost.

## Four strategies, selected by Spring profile

| Profile       | Mechanism                                                     | Cost                                                                                                  |
|---------------|---------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| `naive`       | read, compute in memory, write back                           | lost updates                                                                                          |
| `optimistic`  | `@Version`: `UPDATE ... WHERE version = ?`, retry when 0 rows | under contention, work is retried or rejected (HTTP 409)                                              |
| `pessimistic` | `SELECT ... FOR UPDATE`                                       | callers queue up; the lock is held while processing; deadlock risk as soon as several rows are locked |
| `atomic`      | `UPDATE ... SET balance = balance - ? WHERE balance >= ?`     | the rule lives in SQL and bypasses the entity                                                         |

## Run it

1. `docker compose up -d` (from the repository root)
2. `./scripts/compare.sh` runs the four strategies one after the other and prints a comparison.
   `./scripts/compare.sh naive` runs only the bug.

To run from the IDE, create the database once:
`docker compose exec postgres psql -U demo -d postgres -c "CREATE DATABASE day02"`
then start the app with a profile (`-Dspring.profiles.active=optimistic`, for example).

Requirements: Docker, Java 21, Maven, `curl`, `jq`. Set `NO_COLOR=1` to disable colors, `PORT` to change the port.

## Tests

`mvn test` (Docker is required for Testcontainers).

## Limits

- One row only. Moving money between two accounts adds a new problem: locking rows in inconsistent order causes
  deadlocks.
- The processing delay is simulated (`demo.processing-delay`) to make the race reproducible; real applications have real
  work in that gap.
- The relative timings depend on the machine and on the delay: read the correctness columns, not the milliseconds.