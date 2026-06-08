# Frontend (`pfe_front`)

Angular 19 SPA for authentication, dashboard pages, and the live infrastructure screen.

## Stack

- Angular 19 (standalone components)
- Tailwind CSS v4
- JWT auth interceptor/guards
- STOMP over SockJS for real-time updates

## Main Pages

- `/login`, `/register`
- `/dashboard`
- `/images/safe`, `/images/unsafe`
- `/pipelines`, `/alerts`
- `/infra-live` (real-time Azure inventory)
- `/admin`

## API and WebSocket Targets

`src/environments/environment.ts` (local):

- `apiBaseUrl: http://localhost:8080/api`
- `wsUrl: http://localhost:8080/ws`

Production build uses relative URLs from `environment.production.ts` so nginx can proxy `/api` and `/ws`.

## Run Locally

```powershell
Push-Location "C:\Users\fathi\OneDrive\Desktop\PFE\pfe_front"
npm start
```

Open `http://localhost:4200`.

## Build

```powershell
Push-Location "C:\Users\fathi\OneDrive\Desktop\PFE\pfe_front"
npm run build
```

## How `infra-live` gets data

1. Initial REST load from `GET /api/infra/resources`.
2. Sync history from `GET /api/infra/sync/runs`.
3. Live row updates from WebSocket topics:
   - `/topic/resources`
   - `/topic/vm-status`

If backend has no synced Azure data yet, the page shows an empty-state message.
