import {createRemoteJWKSet, jwtVerify} from 'jose';

const googleKeys = new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com');
let keyResolver = createRemoteJWKSet(googleKeys);

export function setFirebaseKeyResolver(resolver) {
  keyResolver = resolver;
}

export const firebaseConfigured = () => Boolean(process.env.FIREBASE_PROJECT_ID?.trim());

export async function verifyFirebaseIdToken(idToken) {
  const projectId = process.env.FIREBASE_PROJECT_ID?.trim();
  if (!projectId) throw new Error('FIREBASE_PROJECT_ID is required');
  const {payload} = await jwtVerify(idToken, keyResolver, {
    algorithms: ['RS256'],
    issuer: 'https://securetoken.google.com/' + projectId,
    audience: projectId,
    requiredClaims: ['sub', 'auth_time', 'exp', 'iat']
  });
  const now = Math.floor(Date.now() / 1000);
  if (typeof payload.sub !== 'string' || !payload.sub || payload.sub.length > 128) {
    throw new Error('Invalid Firebase subject');
  }
  if (!Number.isInteger(payload.auth_time) || payload.auth_time < 0 || payload.auth_time > now) {
    throw new Error('Invalid Firebase authentication time');
  }
  if (!Number.isInteger(payload.iat) || payload.iat > now) throw new Error('Invalid Firebase issue time');
  return payload;
}
