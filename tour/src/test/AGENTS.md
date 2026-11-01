# Testing rules

- Runner: JUnit 5 via Spring Boot test starters. Mockito comes from the starters; do not add extra test frameworks.
- Run all: `./mvnw test`
- Run one class: `./mvnw -Dtest=TourApplicationTests test`
- Run one method: `./mvnw -Dtest=TourApplicationTests#contextLoads test`
- Current mix:
  - `@WebMvcTest` + `@AutoConfigureMockMvc` for controllers/security (`SecurityConfigTest`, `AuthControllerTest`, `TourControllerTest`).
  - Mockito unit tests for services/security (`AuthServiceTest`, `JwtServiceTest`, `RateLimitFilterTest`, etc.).
  - `@SpringBootTest` integration tests (`BookingFlowIntegrationTests`, `CrudIntegrationTests`) need a running PostgreSQL.
- `TourApplicationTests.contextLoads` is currently skipped because it needs PostgreSQL.
- Integration tests need PostgreSQL matching `SPRING_DATASOURCE_URL`/`SPRING_DATASOURCE_USERNAME`/`SPRING_DATASOURCE_PASSWORD` (optional `.env` import).
- No H2 and no Testcontainers. Do not add H2 as a shortcut; tests must exercise PostgreSQL constraints and Flyway migrations.
- Test startup runs Flyway and Hibernate `validate`, so it also catches schema/entity drift.
- Default datasource is `jdbc:postgresql://localhost:5432/tour` with `postgres`/`postgres` unless env vars override it. Never point tests at a shared or production database.
- Add a test for every behavior change. Prefer service-level integration tests for booking, payment, and refund rules.
- Do not mock repositories to test seat limits, payment sums, or refund caps; those guarantees include the database/locking behavior.
- Test config lives in `src/test/resources/application-test.properties`; add shared fixtures there.
- Keep one focused assertion-based check per new rule; no test framework beyond the existing starters.
