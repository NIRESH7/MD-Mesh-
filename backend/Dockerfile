FROM node:18-alpine

WORKDIR /workspace

COPY backend/package.json /workspace/backend/
WORKDIR /workspace/backend
RUN npm install --omit=dev

COPY backend /workspace/backend
COPY admin-panel /workspace/admin-panel
COPY database /workspace/database

ENV NODE_ENV=production
EXPOSE 3034

CMD ["sh", "-c", "node scripts/seed.js && node src/server.js"]
