# Testing rules

- Runner: JUnit 5 via Spring Boot test starters. No Mockito/JUnit deps beyond the starters.
- Run all: `./mvnw test`
- Run one class: `./mvnw -Dtest=TourApplicationTests test`
- Run one method: `./mvnw -Dtest=TourApplicationTests#contextLoads test`
- Tests are `@SpringBootTest` integration tests and need a running PostgreSQL matching `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`.
- No H2 and no Testcontainers. Do not add H2 as a shortcut; tests must exercise PostgreSQL constraints and Flyway migrations.
- Test startup runs Flyway and Hibernate `validate`, so it also catches schema/entity drift.
- Default datasource points at the local `quanlybantour` database. Never point tests at a shared or production database.
- Add a test for every behavior change. Prefer service-level integration tests for booking, payment, and refund rules.
- Do not mock repositories to test seat limits, payment sums, or refund caps; those guarantees include the database/locking behavior.
- Put shared fixtures in `src/test/resources/`. There are none yet.
- Keep one focused assertion-based check per new rule; no test framework beyond the existing starters.
