import {cleanupExpiredRecords} from '../backend/maintenance.js';
import {pool} from '../backend/db.js';

try { console.log(await cleanupExpiredRecords(5000)); }
finally { await pool.end(); }
