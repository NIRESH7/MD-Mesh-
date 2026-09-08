# Setup guide

## Option A — Docker (recommended)

Requirements: Docker Desktop.

```powershell
cd "C:\Users\Admin\Desktop\MD Mesh"
docker compose up --build
```

- Admin panel + API: http://localhost:5000
- MySQL: localhost:3306, user `root`, password `mdmesh_root_pass`
- Login: `admin` / `password123`

Change `ADMIN_PASSWORD` and `JWT_SECRET` in `docker-compose.yml` before any production use.

Load demo devices:

```powershell
docker compose exec api node scripts/mock-devices.js
```

Stop:

```powershell
docker compose down
```

Data lives in the `mdmesh_mysql` volume. `docker compose down -v` deletes it.

## Option B — Node + MySQL on the machine

1. Install [Node.js 18+](https://nodejs.org/) and MySQL 8.
2. Create a user or use root. Copy env:

```powershell
cd backend
copy .env.example .env
```

3. Edit `.env`:

```
DB_HOST=localhost
DB_USER=root
DB_PASSWORD=your_mysql_password
DB_NAME=device_manager
JWT_SECRET=a_random_string_at_least_32_characters
PORT=5000
```

4. Install, seed, start:

```powershell
npm install
npm run seed
npm start
```

5. Open http://localhost:5000

## Admin panel without the API static server

The API already serves `admin-panel/`. If you open the HTML from disk, set the API base in the browser console:

```js
localStorage.setItem('mdmesh.apiBase', 'http://localhost:5000');
```

Then reload. CORS allows non-production origins by default.

## Android agent

See [APK.md](APK.md). Physical devices must use the computer's LAN address (`http://192.168.x.x:5000`), not `localhost`.

Windows Firewall: allow inbound TCP 5000 if phones cannot sync.
