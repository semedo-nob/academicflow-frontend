-- Department-scoped memberships (ROLE + INSTITUTION + DEPARTMENT)
-- Reuses organization_nodes as departments; does not invent a parallel departments table.

CREATE TABLE organization_memberships (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_node_id UUID NOT NULL REFERENCES organization_nodes(id),
    role VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_org_membership UNIQUE (tenant_id, user_id, organization_node_id, role)
);

CREATE INDEX idx_org_memb_user ON organization_memberships(tenant_id, user_id);
CREATE INDEX idx_org_memb_node ON organization_memberships(tenant_id, organization_node_id);
CREATE INDEX idx_org_memb_role ON organization_memberships(tenant_id, role, status);

-- At most one ACTIVE Department Chair per department node
CREATE UNIQUE INDEX uq_one_active_chair_per_department
    ON organization_memberships (tenant_id, organization_node_id)
    WHERE role = 'DEPARTMENT_CHAIR' AND status = 'ACTIVE';

-- Lightweight student roster for future student-facing scope (optional association)
CREATE TABLE students (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    organization_node_id UUID NOT NULL REFERENCES organization_nodes(id),
    programme VARCHAR(200),
    student_number VARCHAR(80) NOT NULL,
    full_name VARCHAR(200) NOT NULL,
    email VARCHAR(200),
    year_of_study INT,
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    user_id UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_student_number_tenant UNIQUE (tenant_id, student_number)
);

CREATE INDEX idx_students_dept ON students(tenant_id, organization_node_id);

-- Backfill memberships from existing users.organization_node_id
INSERT INTO organization_memberships (id, tenant_id, user_id, organization_node_id, role, status, is_primary, created_at)
SELECT
    gen_random_uuid(),
    u.tenant_id,
    u.id,
    u.organization_node_id,
    u.role,
    CASE WHEN u.active AND u.account_status = 'ACTIVE' THEN 'ACTIVE' ELSE 'INACTIVE' END,
    TRUE,
    COALESCE(u.created_at, NOW())
FROM users u
WHERE u.organization_node_id IS NOT NULL
  AND u.role IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM organization_memberships m
      WHERE m.tenant_id = u.tenant_id
        AND m.user_id = u.id
        AND m.organization_node_id = u.organization_node_id
        AND m.role = u.role
  );

-- Ensure Mathematics has an active chair membership for demo Math chair if present
INSERT INTO organization_memberships (id, tenant_id, user_id, organization_node_id, role, status, is_primary, created_at)
SELECT
    gen_random_uuid(),
    u.tenant_id,
    u.id,
    u.organization_node_id,
    'DEPARTMENT_CHAIR',
    'ACTIVE',
    TRUE,
    NOW()
FROM users u
WHERE lower(u.email) = lower('m.otieno@uonbi.ac.ke')
  AND u.organization_node_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM organization_memberships m
      WHERE m.user_id = u.id AND m.role = 'DEPARTMENT_CHAIR' AND m.status = 'ACTIVE'
  );
