-- User invitations (institution admin invites colleagues)

CREATE TABLE IF NOT EXISTS invitations (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL REFERENCES tenants(id),
    email                VARCHAR(255) NOT NULL,
    full_name            VARCHAR(200) NOT NULL,
    role                 VARCHAR(40)  NOT NULL DEFAULT 'VIEWER',
    organization_node_id UUID REFERENCES organization_nodes(id),
    token                VARCHAR(64)  NOT NULL UNIQUE,
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    invited_by           UUID,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at           TIMESTAMPTZ  NOT NULL,
    accepted_at          TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_invitations_tenant ON invitations(tenant_id);
CREATE INDEX IF NOT EXISTS idx_invitations_email ON invitations(tenant_id, email);
CREATE INDEX IF NOT EXISTS idx_invitations_token ON invitations(token);
