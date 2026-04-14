# QWEN.md - Maidav

## Project Overview

**Maidav** is a Spring Boot 3.5 (Java 21) web application for managing sales, quotes, clients, products, credit accounts, and collections. It uses a server-side rendered architecture with Thymeleaf templates, PostgreSQL for persistence, and Flyway for database migrations.

### Core Domain Modules

| Module | Description |
|---|---|
| **Clients & Zones** | Client management with national ID validation, geographic zone assignment |
| **Products & Providers** | Product catalog with provider relationships, price adjustments, barcode generation (EAN-13) |
| **Sales & Credit Accounts** | Sale creation with installment-based credit, payment tracking, morosity management |
| **Quotes** | Quote generation with pricing plans, financing calculations, document (PDF) export |
| **Collections** | Collection workbench for tracking and managing overdue installment payments |
| **Users & Roles** | Role-based access control (RBAC) with roles, permissions, and password recovery |
| **Dashboard** | Portfolio snapshots and dashboard analytics |
| **Settings** | Company configuration including calculator settings for financing |

### Technology Stack

- **Backend:** Spring Boot 3.5.9, Java 21
- **ORM:** Spring Data JPA (Hibernate)
- **Database:** PostgreSQL 16
- **Migrations:** Flyway (41+ migration scripts in `db/migration`)
- **Templating:** Thymeleaf with Spring Security integration
- **Security:** Spring Security with custom authentication refresh filter
- **PDF Generation:** OpenPDF
- **Barcode:** WebP ImageIO + custom EAN-13 SVG renderer
- **Boilerplate:** Lombok
- **Build:** Maven
- **Containerization:** Docker (multi-stage build, JRE slim runtime)
- **Deployment:** Render (`render.yaml`), Docker Compose for local

## Project Structure

```
maidav/
├── src/main/java/com/sales/maidav/
│   ├── config/              # Security, web, datasource configuration
│   ├── model/               # JPA entities (client, product, sale, quote, user, etc.)
│   ├── repository/          # Spring Data JPA repositories
│   ├── service/             # Business logic services
│   └── web/controller/      # Thymeleaf web controllers
├── src/main/resources/
│   ├── db/migration/        # Flyway SQL migrations (V1__ through V41+)
│   ├── static/              # CSS, favicon, static assets
│   └── templates/           # Thymeleaf templates (layout, fragments, pages)
├── src/test/java/           # Unit and integration tests
├── docker/entrypoint.sh     # Docker container entrypoint script
├── docker-compose.yml       # Local dev with PostgreSQL + app
├── Dockerfile               # Multi-stage Maven + JRE build
├── render.yaml              # Render.com deployment config
└── .codex/                  # AI agent definitions and workflows
```

## Building and Running

### Prerequisites

- Java 21 (Temurin recommended)
- Maven 3.9+
- PostgreSQL 15+ (for local development)
- Docker & Docker Compose (optional, for containerized dev)

### Local Development (without Docker)

1. **Ensure PostgreSQL is running** with a database matching `application-local.yml`:
   - Database: `sales_db`
   - User: `sales`
   - Password: `sales123`

2. **Run with the `local` profile:**
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
   ```
   The app will start on `http://localhost:8080`.

### Local Development (with Docker Compose)

```bash
docker compose up --build
```

This starts PostgreSQL and the Spring Boot app together. The app is exposed on port `10000` by default.

### Build

```bash
./mvnw clean package -DskipTests
```

### Run Tests

```bash
./mvnw test
```

### Package for Deployment

```bash
./mvnw clean package
```

The resulting Jr is at `target/maidav-0.0.1-SNAPSHOT.jar`.

### Environment Variables

| Variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | (from DATABASE_URL) | JDBC URL for PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | (from DATABASE_USERNAME) | DB username |
| `SPRING_DATASOURCE_PASSWORD` | (from DATABASE_PASSWORD) | DB password |
| `PORT` | `10000` | HTTP port |
| `APP_UPLOAD_DIR` | `uploads` | Directory for file uploads |
| `SPRING_JPA_SHOW_SQL` | `false` | Log SQL queries |

## Development Conventions

### Architecture Patterns

- **Layered architecture:** Controllers → Services → Repositories → Entities
- **Interface + Implementation:** Services define an interface (e.g., `QuoteService`) with a `*Impl` class (e.g., `QuoteServiceImpl`)
- **Support classes:** Complex logic is extracted into support classes (e.g., `QuoteCalculator`, `QuotePricingSupport`, `CreditPaymentPricingSupport`)
- **Custom exceptions:** Domain-specific exceptions extend `RuntimeException` (e.g., `InvalidQuoteException`, `InvalidSaleException`, `DuplicateProductCodeException`)
- **JPA Auditing:** `@EnableJpaAuditing` is enabled; entities extend `BaseEntity` for audit fields (`createdAt`, `updatedAt`)

### Database Migrations

- All schema changes go through **Flyway** migrations in `src/main/resources/db/migration/`
- Naming convention: `V<N>__<description>.sql`
- Current version: V41+ (as of the latest migration)
- Never modify existing migration files; create a new one instead

### Security

- Spring Security with role-based permissions
- Custom `AuthenticationRefreshFilter` for session/token handling
- `CustomAccessDeniedHandler` for 403 responses
- Password reset flow via `PasswordResetToken` entity

### Testing

- Tests exist for key services (`QuoteServiceImplTest`, `SaleServiceImplTest`) and controllers (`QuoteControllerTest`)
- Use Spring's test framework and security test utilities

### Code Style

- Lombok is used to reduce boilerplate (`@Data`, `@AllArgsConstructor`, `@NoArgsConstructor`, etc.)
- Follow existing naming conventions in the codebase
- Before implementing, analyze functional and technical impact (see AGENTS.md)

## AI Agent Workflows

The project defines custom AI agent workflows in `.codex/`:

| Workflow | Purpose |
|---|---|
| `.codex/workflows/feature.md` | Guide for implementing new features |
| `.codex/workflows/bugfix.md` | Guide for fixing bugs |

And agent roles:

| Agent | Purpose |
|---|---|
| `analyst` | Requirements and impact analysis |
| `developer` | Code implementation |
| `qa` | Testing and quality assurance |
| `devops` | Deployment and infrastructure |
| `optimizer` | Performance and code quality improvements |
| `db-sync-testing` | Database synchronization procedures |

## Deployment

### Render.com

Configured via `render.yaml`:
- Web service: `maidav-app` (Docker-based)
- Database: Managed PostgreSQL (`maidav-db`)
- Disk: 1 GB persistent volume for uploads
- Auto-deploy on push
- Health check: `/login`

### Docker

The Dockerfile uses a multi-stage build:
1. **Builder:** Maven 3.9 + Temurin 21 — builds the JAR
2. **Runtime:** Temurin 21 JRE — runs the application

Entrypoint: `docker/entrypoint.sh`

## Key Files

| File | Purpose |
|---|---|
| `pom.xml` | Maven dependencies and build config |
| `docker-compose.yml` | Local dev environment (app + DB) |
| `Dockerfile` | Production Docker image |
| `render.yaml` | Render.com deployment config |
| `application.yml` | Default Spring Boot config |
| `application-local.yml` | Local development overrides |
| `INSTRUCTIVO_RESTORE_DOCKPLOY_A_LOCAL.md` | Step-by-step guide for syncing prod DB/uploads to local |
| `AGENTS.md` | AI agent configuration and rules |
