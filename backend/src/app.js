const path = require('path');
const fs = require('fs');
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

  const reactAdminDir = path.join(__dirname, '../../admin-web/dist');
  const legacyAdminDir = path.join(__dirname, '../../admin-panel');
  const adminDir = process.env.ADMIN_DIR
    || (fs.existsSync(path.join(reactAdminDir, 'index.html')) ? reactAdminDir : legacyAdminDir);
  app.use(express.static(adminDir, {
    etag: false,
    lastModified: false,
    setHeaders(res, filePath) {
      if (env.nodeEnv !== 'production') {
        res.setHeader('Cache-Control', 'no-store');
      } else if (/\.(html|js|css)$/i.test(filePath)) {
        res.setHeader('Cache-Control', 'no-cache');
      }
    }
  }));
  app.get(['/', '/app.html', '/index.html'], (req, res, next) => {
    const index = path.join(adminDir, 'index.html');
    if (fs.existsSync(index)) return res.sendFile(index);
    return next();
  });

  app.use(notFound);
  app.use(errorHandler);

  return app;
}

module.exports = { createApp };
