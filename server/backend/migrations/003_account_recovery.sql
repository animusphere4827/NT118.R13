CREATE TABLE password_resets (
  email VARCHAR(254) PRIMARY KEY,
  user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
  nonce UUID NOT NULL,
  otp_hash CHAR(64) NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,
  verified_at TIMESTAMPTZ,
  reset_hash CHAR(64),
  reset_expires_at TIMESTAMPTZ,
  password_fingerprint CHAR(64),
  CHECK (expires_at > created_at)
);
CREATE INDEX idx_password_resets_expiry ON password_resets(expires_at);
CREATE TABLE avatar_assets (
  id UUID PRIMARY KEY,
  user_id INTEGER NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  object_key VARCHAR(200) NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_auth_sessions_revoked ON auth_sessions(revoked_at) WHERE revoked_at IS NOT NULL;
