-- Platform product-owner command center

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS onboarding_stage VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS plan_code VARCHAR(40) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN IF NOT EXISTS last_activity_at TIMESTAMPTZ;

UPDATE tenants SET onboarding_stage = CASE
    WHEN status = 'PENDING' THEN 'REQUESTED'
    WHEN status = 'APPROVED' THEN 'ACTIVE'
    WHEN status = 'SUSPENDED' THEN 'SUSPENDED'
    WHEN status = 'REJECTED' THEN 'REJECTED'
    ELSE onboarding_stage
END;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS failed_login_count INT NOT NULL DEFAULT 0;

UPDATE users SET account_status = CASE WHEN active THEN 'ACTIVE' ELSE 'DEACTIVATED' END;

CREATE TABLE IF NOT EXISTS feature_flags (
    id          UUID PRIMARY KEY,
    flag_key    VARCHAR(80) NOT NULL UNIQUE,
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(255),
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS platform_settings (
    id            UUID PRIMARY KEY,
    setting_key   VARCHAR(80) NOT NULL UNIQUE,
    setting_value VARCHAR(500) NOT NULL,
    description   VARCHAR(255),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS security_events (
    id          UUID PRIMARY KEY,
    severity    VARCHAR(20) NOT NULL DEFAULT 'LOW',
    event_type  VARCHAR(80) NOT NULL,
    email       VARCHAR(255),
    tenant_id   UUID,
    details     VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_security_events_created ON security_events(created_at DESC);

INSERT INTO feature_flags (id, flag_key, name, description, enabled) VALUES
  ('77777777-7777-7777-7777-777777777701', 'teaching_requests', 'Teaching Requests', 'Cross-department teaching requests', TRUE),
  ('77777777-7777-7777-7777-777777777702', 'smart_recommendations', 'Smart Recommendations', 'Lecturer matching engine', TRUE),
  ('77777777-7777-7777-7777-777777777703', 'import_engine', 'Import Engine', 'CSV/PDF import pipeline', TRUE),
  ('77777777-7777-7777-7777-777777777704', 'timetable', 'Timetable', 'Timetable views and exports', TRUE),
  ('77777777-7777-7777-7777-777777777705', 'advanced_reports', 'Advanced Reports', 'Extended reporting packs', TRUE),
  ('77777777-7777-7777-7777-777777777706', 'beta_analytics', 'Beta Analytics', 'Experimental product analytics', FALSE)
ON CONFLICT (flag_key) DO NOTHING;

INSERT INTO platform_settings (id, setting_key, setting_value, description) VALUES
  ('88888888-8888-8888-8888-888888888801', 'product_name', 'AcademicFlow', 'Product display name'),
  ('88888888-8888-8888-8888-888888888802', 'default_timezone', 'Africa/Nairobi', 'Default platform timezone'),
  ('88888888-8888-8888-8888-888888888803', 'maintenance_mode', 'false', 'Block institution logins when true'),
  ('88888888-8888-8888-8888-888888888804', 'max_upload_mb', '20', 'Default upload size limit'),
  ('88888888-8888-8888-8888-888888888805', 'session_hint', 'local', 'Auth mode hint for operators')
ON CONFLICT (setting_key) DO NOTHING;
