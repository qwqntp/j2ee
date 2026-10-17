# Tech Stack

## Overview

| Layer          | Technology              |
|----------------|-------------------------|
| Frontend       | Next.js                 |
| Backend        | Spring Boot             |
| Persistence    | Spring Data JPA         |
| Security       | Spring Security         |
| Authentication | Access/Refresh token, OAuth2 |
| Messaging      | RabbitMQ                |
| Caching        | Redis                   |
| Realtime       | WebSocket               |
| Media          | Cloudinary              |
| Testing        | Spring Test             |
| Database       | PostgreSQL, Supabase    |

## Frontend

- **Next.js** — React framework for the web UI.

> Note: the repository currently contains only the backend (`tour/`). No Next.js project is present yet; version and setup are TBD.

## Backend

- **Spring Boot 4.1.1** — application framework (`spring-boot-starter-parent`).
- **Java 21**.
- **Spring MVC** via `spring-boot-starter-webmvc` — REST/HTTP layer.

## Persistence

- **Spring Data JPA** via `spring-boot-starter-data-jpa`.
- **PostgreSQL** — runtime driver (`org.postgresql:postgresql`).

## Security

- **Spring Security** via `spring-boot-starter-security`.

## Authentication & Authorization

- **Access / Refresh token** — short-lived access token + long-lived refresh token (JWT-based).
- **OAuth2** — third-party login (e.g. Google) via Spring Security OAuth2 client.

## Messaging

- **RabbitMQ** — asynchronous messaging / event broker (Spring AMQP).

## Caching

- **Redis** — in-memory cache / session store (Spring Data Redis).

## Realtime

- **WebSocket** — bidirectional real-time communication (Spring `WebSocket` / STOMP).

## Media

- **Cloudinary** — cloud image/media storage and delivery.

## Testing

- **Spring Test** via Spring Boot test starters:
  - `spring-boot-starter-data-jpa-test`
  - `spring-boot-starter-security-test`
  - `spring-boot-starter-webmvc-test`

## Database

- **PostgreSQL** via **Supabase** — primary datastore.


## Build & Tooling

- **Maven** — build and dependency management.
- **Lombok** — boilerplate reduction (compile-time annotation processor).

## Configuration

- `tour/src/main/resources/application.properties` — `spring.application.name=tour`.
