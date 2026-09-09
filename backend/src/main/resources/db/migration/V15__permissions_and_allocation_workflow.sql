-- Permissions, invitation permission payloads, allocation workflow + comments

ALTER TABLE invitations
    ADD COLUMN IF NOT EXISTS permissions_json TEXT,
    ADD COLUMN IF NOT EXISTS permission_template VARCHAR(64);

ALTER TABLE allocations
    ADD COLUMN IF NOT EXISTS workflow_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT';

UPDATE allocations
SET workflow_status = CASE
    WHEN status IN ('AWAITING_APPROVAL') THEN 'SUBMITTED'
    WHEN status IN ('APPROVED', 'PUBLISHED') THEN 'APPROVED'
    WHEN status IN ('CANCELLED', 'REJECTED') THEN 'REJECTED'
    WHEN status IN ('CONFLICT') THEN 'CHANGES_REQUESTED'
    ELSE 'DRAFT'
END
WHERE workflow_status = 'DRAFT' OR workflow_status IS NULL;

CREATE TABLE IF NOT EXISTS user_permissions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    permission VARCHAR(64) NOT NULL,
    effect VARCHAR(16) NOT NULL,
    organization_node_id UUID,
    granted_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_permissions_effect CHECK (effect IN ('GRANT', 'REVOKE'))
);

CREATE INDEX IF NOT EXISTS idx_user_permissions_user
    ON user_permissions (tenant_id, user_id);

CREATE INDEX IF NOT EXISTS idx_user_permissions_lookup
    ON user_permissions (tenant_id, user_id, permission, effect);

CREATE TABLE IF NOT EXISTS allocation_comments (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    allocation_id UUID NOT NULL,
    author_id UUID NOT NULL,
    body TEXT NOT NULL,
    comment_type VARCHAR(32) NOT NULL DEFAULT 'COMMENT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ,
    resolved_by UUID
);

CREATE INDEX IF NOT EXISTS idx_allocation_comments_allocation
    ON allocation_comments (tenant_id, allocation_id, created_at);
