import {smtpTransport} from '../backend/mailer.js';

await smtpTransport().verify();
console.log('SMTP connection and authentication verified. No email was sent.');
