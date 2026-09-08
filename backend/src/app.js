const path = require('path');
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');
const env = require('./config/env');
const logger = require('./utils/logger');
const apiRoutes = require('./routes');
const { ping } = require('./config/db');
const { notFound, errorHandler } = require('./middleware/errorHandler');

function createApp() {
  const app = express();

  app.set('trust proxy', 1);

  app.use(helmet({
    contentSecurityPolicy: false,
    crossOriginEmbedderPolicy: false
  }));

  const allowed = env.adminOrigin.split(',').map((s) => s.trim()).filter(Boolean);
  app.use(cors({
    origin(origin, callback) {
      if (!origin || allowed.includes(origin) || env.nodeEnv !== 'production') {
        return callback(null, true);
      }
      return callback(null, false);
    },
    credentials: true
  }));

  app.use(express.json({ limit: '5mb' }));
  app.use(express.urlencoded({ extended: false }));

  app.use(morgan(env.nodeEnv === 'production' ? 'combined' : 'dev', {
    stream: { write: (msg) => logger.info(msg.trim()) }
  }));

  app.get('/api/health', async (req, res) => {
    try {
      await ping();
      res.json({ success: true, status: 'ok' });
    } catch (error) {
      res.status(500).json({ success: false, error: 'Database unavailable' });
    }
  });

  app.use('/api', apiRoutes);

  const adminDir = process.env.ADMIN_DIR || path.join(__dirname, '../../admin-panel');
  app.use(express.static(adminDir));

  app.use(notFound);
  app.use(errorHandler);

  return app;
}

module.exports = { createApp };
