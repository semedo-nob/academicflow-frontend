-- Institution signup approval workflow

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN IF NOT EXISTS admin_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS admin_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS decision_note VARCHAR(500),
    ADD COLUMN IF NOT EXISTS decided_at TIMESTAMPTZ;

UPDATE tenants SET status = 'APPROVED' WHERE status IS NULL OR status = '';

-- Platform tenant remains approved
UPDATE tenants
SET status = 'APPROVED'
WHERE id = '11111111-1111-1111-1111-111111111111';
