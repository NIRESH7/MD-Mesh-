# Deployment

## Docker on Ubuntu 20.04+

```bash
sudo apt update && sudo apt install -y docker.io docker-compose-v2
git clone <this-repo> md-mesh && cd md-mesh
# edit docker-compose.yml: JWT_SECRET, ADMIN_PASSWORD, ADMIN_ORIGIN
sudo docker compose up -d --build
```

Put Nginx (or Caddy) in front for HTTPS. Example Nginx:

```nginx
server {
    listen 443 ssl;
    server_name mdm.example.com;
    ssl_certificate     /etc/letsencrypt/live/mdm.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/mdm.example.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:5000;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto https;
    }
}
```

Set in compose:

```
ADMIN_ORIGIN=https://mdm.example.com
NODE_ENV=production
```

Point the APK server URL at `https://mdm.example.com`.

Let's Encrypt:

```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d mdm.example.com
```

## Logs

API logs: container stdout and `logs/server.log` (5 MB rotation, 5 files).

```bash
docker compose logs -f api
```

## Backup

Dump MySQL daily (see DATABASE.md). Keep the volume snapshot if you use a VPS backup product.

## Graceful restart

```bash
docker compose restart api
```

The process handles SIGTERM and closes the MySQL pool.

## Horizontal scale

The API is stateless (JWT). Run several API containers behind Nginx. MySQL remains the single source of lock state. Sticky sessions are not required.
