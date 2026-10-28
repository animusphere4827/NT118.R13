import nodemailer from 'nodemailer';

let cached;
let signature;

export function smtpTransport() {
  const {SMTP_HOST,SMTP_PORT,SMTP_SECURE,SMTP_USER,SMTP_PASSWORD,SMTP_FROM,NODE_ENV} = process.env;
  if (!SMTP_HOST || !SMTP_FROM) throw Object.assign(new Error('Máy chủ chưa cấu hình email'),{status:503});
  const port = Number(SMTP_PORT || 587);
  if (!Number.isInteger(port) || port<1 || port>65535) throw new Error('Invalid SMTP port');
  const local = ['127.0.0.1','localhost','::1'].includes(SMTP_HOST);
  if (NODE_ENV==='production' && (!SMTP_USER || !SMTP_PASSWORD)) throw new Error('SMTP credentials are required in production');
  const nextSignature = JSON.stringify([SMTP_HOST,port,SMTP_SECURE,SMTP_USER,SMTP_PASSWORD,NODE_ENV]);
  if (signature !== nextSignature) {
    cached?.close();
    cached = nodemailer.createTransport({
      host:SMTP_HOST, port, secure:SMTP_SECURE==='true',
      requireTLS:SMTP_SECURE!=='true' && !(local && NODE_ENV!=='production'),
      ...(SMTP_USER?{auth:{user:SMTP_USER,pass:SMTP_PASSWORD}}:{}),
      connectionTimeout:8000, greetingTimeout:8000, socketTimeout:10000,
      disableFileAccess:true, disableUrlAccess:true, logger:false, debug:false
    });
    signature = nextSignature;
  }
  return cached;
}

export async function sendPasswordOtp(email,otp) {
  const transport = smtpTransport();
  await transport.sendMail({
    from:process.env.SMTP_FROM, to:email,
    subject:'Mã xác nhận đặt lại mật khẩu TOEIC',
    text:'Mã xác nhận của bạn là '+otp+'. Mã có hiệu lực trong 5 phút. Không chia sẻ mã này với người khác.'
  });
}
