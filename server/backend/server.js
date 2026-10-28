import {startMaintenance} from './maintenance.js';
import {app} from './app.js';
import {pool} from './db.js';

const port = Number(process.env.PORT || 3000);
const host = process.env.HOST || (process.env.NODE_ENV === 'production' ? '0.0.0.0' : '127.0.0.1');

if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('PORT must be between 1 and 65535');

try {
  const result = await pool.query('SELECT MAX(version) AS version FROM schema_migrations');
  if (result.rows[0].version < 3) throw new Error('Run pnpm migrate before starting the server');
} catch (error) {
  console.error('Database is not ready. Check DATABASE_URL and run pnpm migrate:', error.message);
  await pool.end();
  process.exit(1);
}

const server = app.listen(port, host, () => console.log('TOEIC Studio listening on http://' + host + ':' + port));
server.on('error', async error => {
  console.error('Server could not start:', error.message);
  await pool.end();
  process.exit(1);
});

const stopMaintenance = startMaintenance();
let closing = false;
for (const signal of ['SIGTERM', 'SIGINT']) {
  process.on(signal, () => {
    if (closing) return;
    closing = true;
    stopMaintenance();
    const timeout = setTimeout(() => process.exit(1), 10000).unref();
    server.close(async () => {
      await pool.end();
      clearTimeout(timeout);
      process.exit(0);
    });
    server.closeIdleConnections();
  });
}
