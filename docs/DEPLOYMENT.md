# Deployment — Pandiyan Agency

| Surface | URL |
|---------|-----|
| Admin panel | https://admin.pandiyanagency.com |
| Backend API | https://api.pandiyanagency.com |
| API process port | `3034` |
| Android server URL | `https://api.pandiyanagency.com` |

## Backend env (host panel / `.env`)

Copy values from `backend/.env`. Important keys:

```
PORT=3034
NODE_ENV=production
DB_HOST=localhost
DB_PORT=3306
DB_USER=admin
DB_PASSWORD=...
DB_NAME=api
ADMIN_ORIGIN=https://admin.pandiyanagency.com
API_PUBLIC_URL=https://api.pandiyanagency.com
JWT_SECRET=...          # or jwtadminSecret — same value, min 32 chars
JWT_EXPIRE=24h
passwordSecret=...      # optional (host panel); not used by this API
jwtMemberSecret=...     # optional
jwtSuperAdminSecret=... # optional
jwtEmailSecret=...      # optional
```

This API only signs **admin** JWTs. Fill `JWT_SECRET` / `jwtadminSecret`. The other secrets can stay set for the host panel but are unused.

## Nginx

**API** — `api.pandiyanagency.com` → Node on `3034`:

```nginx
server {
    listen 443 ssl;
    server_name api.pandiyanagency.com;
    ssl_certificate     /etc/letsencrypt/live/api.pandiyanagency.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.pandiyanagency.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:3034;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto https;
    }
}
```

**Admin panel** — `admin.pandiyanagency.com` → static files from `admin-web/dist`:

```nginx
server {
    listen 443 ssl;
    server_name admin.pandiyanagency.com;
    ssl_certificate     /etc/letsencrypt/live/admin.pandiyanagency.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/admin.pandiyanagency.com/privkey.pem;

    root /var/www/admin-pandiyan;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

Build admin before upload:

```bash
cd admin-web && npm ci && npm run build
# upload dist/* to /var/www/admin-pandiyan
```

Admin already calls `https://api.pandiyanagency.com` (`VITE_API_BASE`).

## Start API on server

```bash
cd backend
npm ci --omit=dev
npm run seed
NODE_ENV=production node src/server.js
# or: pm2 start src/server.js --name mdmesh-api
```

Health check: `https://api.pandiyanagency.com/api/health`

Default admin after seed: `admin` / `password123` (change immediately).

## Android

Default server URL is `https://api.pandiyanagency.com`. Rebuild the APK after config changes.

## Logs / restart

```bash
pm2 logs mdmesh-api
pm2 restart mdmesh-api
```

API also writes `logs/server.log`.
