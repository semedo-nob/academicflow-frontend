-- Invitation delivery tracking + hashed-token column (raw token never required after create/resend response)

ALTER TABLE invitations
    ADD COLUMN IF NOT EXISTS token_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS delivery_status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    ADD COLUMN IF NOT EXISTS delivery_error TEXT,
    ADD COLUMN IF NOT EXISTS last_sent_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS email_provider VARCHAR(40);

-- Legacy rows: keep plaintext in token for dual lookup; mirror into token_hash for uniqueness scaffolding
UPDATE invitations
SET token_hash = token
WHERE token_hash IS NULL AND token IS NOT NULL AND token <> '';

CREATE UNIQUE INDEX IF NOT EXISTS idx_invitations_token_hash
    ON invitations(token_hash)
    WHERE token_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_invitations_status_delivery
    ON invitations(tenant_id, status, delivery_status);
