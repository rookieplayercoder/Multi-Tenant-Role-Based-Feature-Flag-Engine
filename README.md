# Feature Flag Engine

A self-hosted, multi-tenant feature-flag and remote-configuration platform — a from-scratch alternative to LaunchDarkly, built to explore the full lifecycle of a real production system: multi-tenant data modeling, role-based authorization, a recursive rule-evaluation engine, SDK authentication, audit trails, config versioning with rollback, and evaluation analytics.

Every organization can have multiple projects, each with multiple environments (dev/test/staging/prod), each hosting its own feature flags. Flags can be simple booleans, percentage rollouts, or fully targeted with a recursive AND/OR/NOT rule tree — including audience segments — and every change is versioned, audited, and (for flags) revertible.

---

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Database Schema](#database-schema)
- [API Endpoints](#api-endpoints)
- [Authentication & Authorization](#authentication--authorization)
- [Getting Started](#getting-started)
- [Testing](#testing)
- [Project Structure](#project-structure)
- [Known Limitations / Roadmap](#known-limitations--roadmap)

---

## Features

- ✅ **Authentication** — JWT-based registration/login for dashboard users
- ✅ **Organizations** — multi-tenant root entity, full CRUD, soft-deletable
- ✅ **Projects** — scoped to an organization, full CRUD
- ✅ **Environments** — scoped to a project (`DEV`/`TEST`/`STAGING`/`PROD`), full CRUD
- ✅ **Feature Flags** — `BOOLEAN`, `PERCENTAGE`, and `TARGETED` evaluation strategies; create, update, enable/disable, change type
- ✅ **Rule Engine** — recursive `GROUP` (`AND`/`OR`/`NOT`) and `CONDITION` rule trees, with independent attribute-matching and percentage-rollout gates on every condition node
- ✅ **Segments** — reusable named audience lists, referenceable from any rule via a reserved `segment` attribute
- ✅ **SDK / API Keys** — per-environment API keys for machine clients, authenticated separately from dashboard JWTs
- ✅ **Audit Logs** — every create/update/delete/enable/disable/evaluate operation recorded with actor, action, resource type, and timestamp
- ✅ **Versioning & Rollback** — every flag/rule change snapshots a new `FlagVersion`; flags can be rolled back to any prior version
- ✅ **Evaluation Metrics** — pre-aggregated, date-bucketed evaluation counters (not one row per evaluation) for dashboard analytics
- ✅ **Role-Based Access Control** — `OWNER` / `ADMIN` / `EDITOR` / `VIEWER`, enforced per-organization on every endpoint
- ✅ **Global Exception Handling** — consistent JSON error shape across the API
- ✅ **Automated Tests** — unit tests (rule evaluator, rollout bucketing) + `@SpringBootTest`/MockMvc integration tests
- ✅ **Postman Collection** — manual end-to-end verification alongside the automated suite

## Architecture

The codebase is organized **by domain, not by layer** — each business concept (`organization`, `project`, `flag`, `rules`, `segment`, `evaluation`, `audit`, `apikey`, `security`, `auth`) owns its entity, repository, service, controller, and DTOs in one package, rather than splitting into top-level `controllers/`, `services/`, `repositories/` folders. Two packages are intentionally cross-cutting: `common` (global exception handling) and `security` (JWT + API-key authentication, shared across every domain).

```
Request
  │
  ▼
Controller  ──▶  validates input (@Valid), resolves the acting user
  │                (@AuthenticationPrincipal), checks role via
  │                OrganizationAuthorizationService, maps entities
  │                to response records
  ▼
Service     ──▶  business rules, transaction boundaries, orchestrates
  │                repositories, triggers audit logging + version
  │                snapshotting on every mutation
  ▼
Repository  ──▶  Spring Data JPA, derived queries only (no native SQL)
  │
  ▼
PostgreSQL  ──▶  schema owned by Flyway migrations, UUID primary keys,
                  soft deletes via deleted_at, CHECK constraints
                  enforcing shape invariants (e.g. GROUP rules must
                  have a logical_operator, CONDITION rules must not)
```

**Two parallel authentication paths**, both populating the same `SecurityContextHolder` via pre-authentication filters ahead of Spring Security's own username/password filter:

- **Dashboard users** → `Authorization: Bearer <JWT>` → `JwtAuthenticationFilter`
- **SDK clients** → `X-Api-Key: <key>` → `ApiKeyAuthenticationFilter`, resolving directly to the `Environment` the key belongs to (no human role check — the key itself is the authorization boundary for machine clients)

**The rule engine** (`evaluation` package) walks a `FeatureRule` tree recursively: `GROUP` nodes combine their children with `AND`/`OR`/`NOT`; `CONDITION` nodes require **both** an attribute check and a rollout-percentage check to pass (either can be absent/vacuously true), which is what lets one node express pure attribute targeting, pure percentage rollout, or both combined. Percentage bucketing is a deterministic SHA-256 hash of `flagKey:userIdentifier` — the same user always lands in the same bucket for a given flag.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.0 |
| Web | Spring MVC (REST) |
| Security | Spring Security 7 (JWT via `jjwt`, custom API-key filter) |
| Persistence | Spring Data JPA + Hibernate 7 |
| Database | PostgreSQL |
| Migrations | Flyway |
| Build | Maven |
| Testing | JUnit 5, MockMvc, `@SpringBootTest` |
| IDs | UUID (v4) primary keys throughout |

## Database Schema

All tables use UUID primary keys and `TIMESTAMPTZ` for timestamps. Soft-deletable entities carry a nullable `deleted_at`; append-only tables (`flag_versions`, `audit_logs`) have no update path at all — only inserts.

| Table | Purpose |
|---|---|
| `organizations` | Tenant root. Slug-unique among non-deleted rows. |
| `users` | Registered accounts. Email-unique among non-deleted rows. |
| `members` | Join table: user ↔ organization, with a `role` (`OWNER`/`ADMIN`/`EDITOR`/`VIEWER`). No soft delete — removal is final. |
| `projects` | Scoped to an organization; key unique within the org. |
| `environments` | Scoped to a project; typed `key` (`DEV`/`TEST`/`STAGING`/`PROD`), unique within the project. |
| `feature_flags` | Scoped to an environment; `flag_type` (`BOOLEAN`/`PERCENTAGE`/`TARGETED`), `enabled`, monotonic `version` counter. |
| `feature_rules` | Self-referencing tree (`parent_rule_id`) under a flag. `GROUP` rows carry a `logical_operator`; `CONDITION` rows carry `attribute`/`operator`/`value`/`rollout_percentage`. A CHECK constraint enforces the shape split. Cascade-deletes subtrees. |
| `segments` | Named audience lists, scoped to an organization. |
| `segment_users` | Composite-keyed (`segment_id`, `user_identifier`) membership rows — no surrogate ID needed. |
| `environment_api_keys` | SDK credentials. Only a hash is ever persisted, never the raw key. `revoked_at` is this table's soft-delete. |
| `flag_versions` | Append-only JSON snapshots of a flag + its full rule tree, one per version number. Powers rollback. |
| `audit_logs` | Append-only. `action` (`AuditAction` enum) + `entity_type` (`ResourceType` enum) + `entity_id`, always attributed to a non-null `actor`. |
| `flag_evaluation_metrics` | Pre-aggregated: one row per `(flag, environment, day, result, reason)`, incremented via atomic upsert — bounded size regardless of traffic volume, unlike one-row-per-evaluation logging. |

## API Endpoints

All endpoints are prefixed `/api`. Auth column: **JWT** = dashboard user (`Authorization: Bearer`), **API Key** = SDK client (`X-Api-Key`), **Public** = no auth.

### Auth
| Method | Path | Auth |
|---|---|---|
| POST | `/auth/register` | Public |
| POST | `/auth/login` | Public |

### Organizations & Members
| Method | Path | Role |
|---|---|---|
| POST | `/organizations` | any authenticated user (becomes `OWNER`) |
| GET | `/organizations/{organizationId}` | any member |
| PUT | `/organizations/{organizationId}` | `OWNER`/`ADMIN` |
| DELETE | `/organizations/{organizationId}` | `OWNER`/`ADMIN` |
| POST | `/organizations/{organizationId}/members` | `OWNER`/`ADMIN` |
| GET | `/organizations/{organizationId}/audit-logs` | any member |

### Projects
| Method | Path | Role |
|---|---|---|
| POST | `/organizations/{organizationId}/projects` | `OWNER`/`ADMIN` |
| GET | `/organizations/{organizationId}/projects` | any member |
| GET | `/projects/{projectId}` | any member |
| PUT | `/projects/{projectId}` | `OWNER`/`ADMIN` |
| DELETE | `/projects/{projectId}` | `OWNER`/`ADMIN` |

### Environments
| Method | Path | Role |
|---|---|---|
| POST | `/projects/{projectId}/environments` | `OWNER`/`ADMIN` |
| GET | `/projects/{projectId}/environments` | any member |
| GET | `/environments/{environmentId}` | any member |
| PUT | `/environments/{environmentId}` | `OWNER`/`ADMIN` |
| DELETE | `/environments/{environmentId}` | `OWNER`/`ADMIN` |

### Feature Flags & Versioning
| Method | Path | Role |
|---|---|---|
| POST | `/environments/{environmentId}/flags` | `OWNER`/`ADMIN`/`EDITOR` |
| GET | `/environments/{environmentId}/flags` | `OWNER`/`ADMIN`/`EDITOR` |
| GET | `/flags/{flagId}` | `OWNER`/`ADMIN`/`EDITOR` |
| PUT | `/flags/{flagId}` | `OWNER`/`ADMIN`/`EDITOR` |
| POST | `/flags/{flagId}/enable` | `OWNER`/`ADMIN`/`EDITOR` |
| POST | `/flags/{flagId}/disable` | `OWNER`/`ADMIN`/`EDITOR` |
| PUT | `/flags/{flagId}/type` | `OWNER`/`ADMIN`/`EDITOR` |
| GET | `/flags/{flagId}/versions` | `OWNER`/`ADMIN`/`EDITOR` |
| GET | `/flags/{flagId}/versions/{version}` | `OWNER`/`ADMIN`/`EDITOR` |
| POST | `/flags/{flagId}/versions/{version}/rollback` | `OWNER`/`ADMIN`/`EDITOR` |
| GET | `/flags/{flagId}/metrics?from=&to=` | `OWNER`/`ADMIN`/`EDITOR` |

### Rules
| Method | Path | Role |
|---|---|---|
| POST | `/flags/{flagId}/rules` | `OWNER`/`ADMIN`/`EDITOR` |
| GET | `/flags/{flagId}/rules` | `OWNER`/`ADMIN`/`EDITOR` |
| GET | `/rules/{ruleId}` | `OWNER`/`ADMIN`/`EDITOR` |
| GET | `/rules/{ruleId}/children` | `OWNER`/`ADMIN`/`EDITOR` |
| PUT | `/rules/{ruleId}` | `OWNER`/`ADMIN`/`EDITOR` |
| PATCH | `/rules/{ruleId}/position` | `OWNER`/`ADMIN`/`EDITOR` |
| DELETE | `/rules/{ruleId}` | `OWNER`/`ADMIN`/`EDITOR` |

### Segments
| Method | Path | Role |
|---|---|---|
| POST | `/organizations/{organizationId}/segments` | `OWNER`/`ADMIN` |
| GET | `/organizations/{organizationId}/segments` | any member |
| GET | `/segments/{segmentId}` | any member |
| PUT | `/segments/{segmentId}` | `OWNER`/`ADMIN` |
| DELETE | `/segments/{segmentId}` | `OWNER`/`ADMIN` |
| POST | `/segments/{segmentId}/members` | `OWNER`/`ADMIN` |
| GET | `/segments/{segmentId}/members` | any member |
| DELETE | `/segments/{segmentId}/members?userIdentifier=` | `OWNER`/`ADMIN` |

### API Keys
| Method | Path | Role |
|---|---|---|
| POST | `/environments/{environmentId}/api-keys` | `OWNER`/`ADMIN` |
| GET | `/environments/{environmentId}/api-keys` | any member |
| DELETE | `/environments/{environmentId}/api-keys/{apiKeyId}` | `OWNER`/`ADMIN` |

### Evaluation
| Method | Path | Auth |
|---|---|---|
| POST | `/environments/{environmentId}/evaluate` | JWT (dashboard testing) |
| POST | `/sdk/evaluate` | API Key (real SDK traffic) |

## Authentication & Authorization

**Dashboard users** register/log in via `/api/auth`, receiving a JWT (`Authorization: Bearer <token>`). Every other dashboard endpoint resolves the caller's role **per organization** via `OrganizationAuthorizationService` — the same user can be `OWNER` of one org and have no access at all to another.

**SDK clients** authenticate with `X-Api-Key`, minted via the dashboard-facing `/api/environments/{id}/api-keys` endpoints. An API key is scoped to exactly one environment; there's no role check for SDK traffic because there's no human to hold a role — the key itself is the trust boundary. Only the key's hash is ever stored.

Role hierarchy (broadest → narrowest access), enforced uniformly:

| Role | Can read | Can create/edit flags, rules, segments | Can manage org/projects/environments/keys |
|---|---|---|---|
| `OWNER` | ✅ | ✅ | ✅ |
| `ADMIN` | ✅ | ✅ | ✅ |
| `EDITOR` | ✅ | ✅ | ❌ |
| `VIEWER` | ✅ | ❌ | ❌ |

## Getting Started

### Prerequisites
- Java 21
- Maven
- PostgreSQL running locally (or reachable)

### 1. Create the database
```bash
createdb feature_flag_engine
```

### 2. Configure `application.properties`
The values below are placeholders — **do not commit real secrets**. Set your own DB credentials and generate a fresh JWT signing key:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/feature_flag_engine
spring.datasource.username=<your-db-username>
spring.datasource.password=<your-db-password>

security.jwt.secret=<base64-encoded-256-bit-key>
security.jwt.expiration-minutes=60
```
Generate a secret quickly with:
```bash
openssl rand -base64 32
```

### 3. Run migrations + start the app
Flyway runs automatically on startup (`spring.flyway.enabled=true`), applying every migration in `src/main/resources/db/migration` against the schema.
```bash
mvn spring-boot:run
```
The API is now available at `http://localhost:8080`.

### 4. Try it
```bash
# Register
curl -X POST localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"a-strong-password","fullName":"Your Name"}'

# Log in
curl -X POST localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"a-strong-password"}'
# → returns a JWT; use it as `Authorization: Bearer <token>` on everything else
```

## Testing

```bash
mvn clean test
```

Two layers of automated coverage:
- **Unit tests** — the rule evaluator and rollout-bucketing logic are pure functions with real edge cases (empty rule trees, missing context attributes, `NOT` groups with zero children, boundary percentages), so they're covered directly without a Spring context.
- **`@SpringBootTest` / MockMvc integration tests** — exercise controllers end-to-end against a real (test) database, covering auth, role enforcement, and the CRUD + evaluation flows.

A Postman collection is also maintained for manual end-to-end verification (e.g. the Module 12 flow: create a segment → add a member → build a `CONDITION` rule with `attribute: "segment"` → confirm a member evaluates differently from a non-member).

## Project Structure

```
src/main/java/com/prateek/featureflag/
├── auth/          # Registration/login controller + DTOs
├── security/      # JWT + API-key filters, SecurityConfig, UserDetailsService
├── common/        # GlobalExceptionHandler (unified error shape)
├── organization/  # Organization + Member entities, services, controller
├── project/       # Project CRUD
├── environment/   # Environment CRUD
├── flag/          # FeatureFlag, FlagVersion, versioning/rollback
├── rules/         # FeatureRule tree + CRUD
├── segment/       # Segment + SegmentUser (audience targeting)
├── evaluation/    # RuleEvaluator, FeatureFlagEvaluationService, dashboard + SDK evaluation controllers
├── apikey/        # EnvironmentApiKey issuance/revocation
├── audit/         # AuditLog, AuditAction, ResourceType
├── metrics/       # Pre-aggregated evaluation analytics
└── user/          # User entity + service
```

Each domain package owns its own entity, repository, service, controller, and `dto/` subpackage — there's no cross-cutting `controllers/`/`services/` split.

## Known Limitations / Roadmap

Being upfront about what's *not* done, rather than implying full coverage:

- **SDK evaluations aren't audited.** `AuditLog.actor` is `NOT NULL`, and there's no human user behind an API-key-authenticated evaluation. Closing this properly needs a schema change (nullable actor, or a "system" pseudo-user) — deliberately not done silently.
- **No API documentation UI yet** (springdoc-openapi/Swagger) — the table above is maintained by hand.
- **No pagination** on list endpoints that can grow unbounded (flags, segments, audit logs) beyond what `audit-logs` already has via `Pageable`.
- **No rate limiting** on `/api/sdk/evaluate`, the only genuinely public high-traffic endpoint.
- Optional stretch ideas not started: webhooks on flag change, scheduled/timed rollouts, a minimal frontend dashboard.
