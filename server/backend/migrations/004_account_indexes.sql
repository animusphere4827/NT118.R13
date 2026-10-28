CREATE UNIQUE INDEX idx_password_resets_token ON password_resets(reset_hash) WHERE reset_hash IS NOT NULL;
