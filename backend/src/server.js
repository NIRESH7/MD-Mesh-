const env = require('./config/env');
const logger = require('./utils/logger');
const { ping, end, ensureSchema } = require('./config/db');
const { createApp } = require('./app');

const app = createApp();

async function start() {
  try {
    await ensureSchema();
    await ping();
    logger.info(`MySQL connected at ${env.db.host}:${env.db.port}/${env.db.database}`);
  } catch (error) {
    logger.error(`Database connection failed: ${error.message}`);
    process.exit(1);
  }

  const server = app.listen(env.port, () => {
    logger.info(`MD Mesh API listening on port ${env.port} (${env.nodeEnv})`);
  });

  const shutdown = async (signal) => {
    logger.info(`${signal} received, shutting down`);
    server.close(async () => {
      await end();
      process.exit(0);
    });
    setTimeout(() => process.exit(1), 10000).unref();
  };

  process.on('SIGINT', () => shutdown('SIGINT'));
  process.on('SIGTERM', () => shutdown('SIGTERM'));
}

start();
