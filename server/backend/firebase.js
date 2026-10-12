// Xác thực Firebase ID token (đăng nhập Google trên Android) theo hướng dẫn
// "Verify ID tokens using a third-party JWT library" của Firebase: RS256, khóa công khai của Google,
// iss = https://securetoken.google.com/<project-id>, aud = <project-id>. Không cần service account.
import {createRemoteJWKSet,jwtVerify} from 'jose';
const GOOGLE_JWKS=new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com');
let keyResolver=createRemoteJWKSet(GOOGLE_JWKS);
// Chỉ dùng trong test để thay khóa của Google bằng khóa tự tạo.
export function setFirebaseKeyResolver(resolver){keyResolver=resolver;}
export const firebaseConfigured=()=>Boolean(process.env.FIREBASE_PROJECT_ID);
export async function verifyFirebaseIdToken(idToken){
 const projectId=process.env.FIREBASE_PROJECT_ID;
 const {payload}=await jwtVerify(idToken,keyResolver,{algorithms:['RS256'],issuer:`https://securetoken.google.com/${projectId}`,audience:projectId,requiredClaims:['sub','auth_time','exp','iat']});
 if(typeof payload.sub!=='string'||!payload.sub||payload.sub.length>128)throw new Error('invalid sub');
 if(payload.auth_time>Math.floor(Date.now()/1000)+60)throw new Error('auth_time in future');
 return payload;
}
