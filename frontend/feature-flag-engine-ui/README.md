# Feature Flag Engine — Frontend

React + TypeScript + Vite + Tailwind + React Router + Axios frontend for the Feature Flag Engine, built incrementally.

## Progress so far
- ✅ Project scaffold (Vite, TS, Tailwind, ESLint config, folder structure)
- ✅ Axios API client with JWT interceptor + global 401 handling
- ✅ Auth context (`login`, `logout`, `isAuthenticated`) backed by `localStorage`
- ✅ Login page (loading + error states)
- ✅ Protected routes (`ProtectedRoute`) guarding everything under the dashboard layout
- ✅ Dashboard shell: responsive Sidebar + Navbar, nav links for all planned modules
- ✅ Organizations CRUD (list, create, edit, delete with confirm dialog)
- ✅ Projects CRUD (list with organization filter, create, edit, delete)
- ✅ Environments CRUD (list with project filter, create, edit, delete)
- ✅ Feature Flags CRUD (list with project filter, create, edit, delete, plus per-environment on/off toggles)
- ✅ Segments CRUD (list with project filter, create, edit, delete, with a dynamic targeting-rule builder)
- ✅ API Keys (list with environment filter and revoke, create with a reveal-once secret view)
- ⏳ Not built yet: Audit Logs (CRUD screens + API wiring) — coming next

## Setup

```bash
npm install
cp .env.example .env   # adjust VITE_API_BASE_URL if needed
npm run dev
```

App runs at `http://localhost:5173`.

## Configuration

`.env`:
```
VITE_API_BASE_URL=http://localhost:8080/api
```

## ⚠️ Assumption you should verify: login response shape

You said you weren't sure how your Spring Boot login endpoint returns the JWT, so `src/api/auth.ts` currently:
- POSTs to `/auth/login` with `{ email, password }`
- Reads the token from **either** `data.token` or `data.accessToken` in the JSON response body

If your backend actually returns the JWT in a response **header** (e.g. `Authorization: Bearer ...`) instead of the body, or uses a different field name / login path, update `src/api/auth.ts` accordingly — that's the only file that needs to change.

## ⚠️ Assumption you should verify: Organizations endpoints

`src/api/organizations.ts` assumes standard REST conventions:
- `GET /organizations` → `Organization[]`
- `GET /organizations/{id}` → `Organization`
- `POST /organizations` with `{ name, slug?, description? }`
- `PUT /organizations/{id}` with the same shape
- `DELETE /organizations/{id}`

If your controller mappings, field names, or response wrapping (e.g. `{ data: [...] }` instead of a raw array) differ, adjust that one file — the list and form pages don't need to change.

## ⚠️ Assumption you should verify: Projects endpoints

`src/api/projects.ts` assumes a **flat** `/projects` resource where each project has an `organizationId` field, and standard REST conventions (`GET/POST /projects`, `GET/PUT/DELETE /projects/{id}`). The list page also calls `GET /projects?organizationId=...` when filtering by organization.

If your API instead nests projects under an organization (e.g. `/organizations/{orgId}/projects`), update `src/api/projects.ts` — the list and form pages don't need to change.

## ⚠️ Assumption you should verify: Environments endpoints

`src/api/environments.ts` assumes a **flat** `/environments` resource where each environment has a `projectId` field, and standard REST conventions (`GET/POST /environments`, `GET/PUT/DELETE /environments/{id}`). The list page also calls `GET /environments?projectId=...` when filtering by project.

If your API instead nests environments under a project (e.g. `/projects/{projectId}/environments`), update `src/api/environments.ts` — the list and form pages don't need to change.

## ⚠️ Assumption you should verify: Feature Flags endpoints (especially environment toggles)

`src/api/featureFlags.ts` assumes:
- A flat `/flags` resource with a `projectId` field, using standard REST conventions (`GET/POST /flags`, `GET/PUT/DELETE /flags/{id}`), filterable via `GET /flags?projectId=...`
- Flags have a `type` (`boolean` / `string` / `number` / `json`) — adjust the `FlagType` union in `src/types/featureFlag.ts` if your engine models this differently
- **Per-environment enabled state** — the part we're least confident about — is read via `GET /flags/{id}/environments` (expected to return an array of `{ environmentId, environmentName?, enabled }`) and updated via `PUT /flags/{id}/environments/{environmentId}` with `{ enabled }`

This last piece is a reasonable guess at how a Spring Boot feature-flag API would expose per-environment toggles, but it's the most likely thing to need adjusting. Everything else in `FeatureFlagFormPage` (the "Environment overrides" section) reads from `getFlagEnvironmentStates` / `setFlagEnvironmentState`, so you only need to edit `src/api/featureFlags.ts` to match your actual endpoints.

## ⚠️ Assumption you should verify: Segments endpoints

`src/api/segments.ts` assumes a flat `/segments` resource with a `projectId` field and an **inline `rules` array** on the segment itself (`{ attribute, operator, value }[]`), sent/received as part of the same `POST`/`PUT` payload as the segment's name/description. The operator set in `src/types/segment.ts` (`equals`, `not_equals`, `contains`, `in`, `greater_than`, `less_than`) is a common baseline — narrow or extend it to match what your rule engine actually supports.

If your API manages rules as a separate sub-resource (e.g. `/segments/{id}/rules`), update `src/api/segments.ts` — the list and form pages don't need to change.

## ⚠️ Assumption you should verify: API Keys endpoints

`src/api/apiKeys.ts` assumes a flat `/api-keys` resource scoped to an `environmentId`, with keys treated as create-once / rename-or-revoke afterwards (the secret itself is never re-fetchable, matching how most API key systems work):
- `GET /api-keys?environmentId=...` → masked keys, no secret
- `POST /api-keys` → returns the full secret **once**, in the same response
- `PUT /api-keys/{id}` → rename only, `{ name }`
- `DELETE /api-keys/{id}` → revoke

Key `type` (`server` vs `client`) is a common convention (full-access vs safe-for-browser) — drop it from `src/types/apiKey.ts` if your engine doesn't distinguish key types. If your backend returns the secret via a separate endpoint or doesn't mask keys in the list response, adjust `src/api/apiKeys.ts` accordingly.

## Folder structure

```
src/
  api/            # axios client + endpoint-specific request functions
  components/
    ui/           # Button, Input, Card, Spinner, ErrorMessage
    layout/       # Sidebar, Navbar, DashboardLayout, nav config
  context/        # AuthContext
  hooks/          # useAuth
  pages/
    auth/         # LoginPage
    dashboard/    # DashboardPage
  routes/         # ProtectedRoute
  types/          # shared TypeScript types
  App.tsx
  main.tsx
```

Each future feature (Organizations, Projects, etc.) will get its own `pages/<feature>/` folder with list/detail/form views, plus a matching `api/<feature>.ts` and `types/<feature>.ts`.
