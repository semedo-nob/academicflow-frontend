-- Admin configuration tables

CREATE TABLE organization_types (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    name        VARCHAR(80) NOT NULL,
    level_no    INT NOT NULL DEFAULT 1,
    description VARCHAR(255),
    UNIQUE (tenant_id, name)
);

CREATE TABLE roles (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL REFERENCES tenants(id),
    code         VARCHAR(40) NOT NULL,
    name         VARCHAR(80) NOT NULL,
    permissions  TEXT NOT NULL DEFAULT '',
    description  VARCHAR(255),
    UNIQUE (tenant_id, code)
);

CREATE TABLE institutional_rules (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    rule_key    VARCHAR(80) NOT NULL,
    rule_value  VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    UNIQUE (tenant_id, rule_key)
);

CREATE TABLE system_settings (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL REFERENCES tenants(id),
    setting_key  VARCHAR(80) NOT NULL,
    setting_value VARCHAR(255) NOT NULL,
    description  VARCHAR(255),
    UNIQUE (tenant_id, setting_key)
);

CREATE TABLE import_mapping_profiles (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL REFERENCES tenants(id),
    name         VARCHAR(120) NOT NULL,
    entity_type  VARCHAR(80) NOT NULL,
    column_map   TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, name)
);

INSERT INTO organization_types (id, tenant_id, name, level_no, description) VALUES
  ('33333333-3333-3333-3333-333333333301', '11111111-1111-1111-1111-111111111111', 'University', 1, 'Top-level institution'),
  ('33333333-3333-3333-3333-333333333302', '11111111-1111-1111-1111-111111111111', 'School', 2, 'Faculty / school under university'),
  ('33333333-3333-3333-3333-333333333303', '11111111-1111-1111-1111-111111111111', 'Department', 3, 'Teaching department');

INSERT INTO roles (id, tenant_id, code, name, permissions, description) VALUES
  ('44444444-4444-4444-4444-444444444401', '11111111-1111-1111-1111-111111111111', 'SUPER_ADMIN', 'Super Admin',
   'users,roles,institutions,organization,years,semesters,rules,settings,imports,audit,allocations,approvals',
   'Full platform access'),
  ('44444444-4444-4444-4444-444444444402', '11111111-1111-1111-1111-111111111111', 'DEPARTMENT_CHAIR', 'Department Chair',
   'lecturers,units,requests,recommendations,allocations,workload,timetable,conflicts,approvals,reports',
   'Department teaching allocation'),
  ('44444444-4444-4444-4444-444444444403', '11111111-1111-1111-1111-111111111111', 'SCHOOL_DEAN', 'School Dean',
   'organization,units,requests,approvals,reports,workload',
   'School-level oversight'),
  ('44444444-4444-4444-4444-444444444404', '11111111-1111-1111-1111-111111111111', 'VIEWER', 'Viewer',
   'dashboard,reports,timetable,workload',
   'Read-only access');

INSERT INTO institutional_rules (id, tenant_id, rule_key, rule_value, description) VALUES
  ('55555555-5555-5555-5555-555555555501', '11111111-1111-1111-1111-111111111111', 'max_workload_hours', '12', 'Default maximum teaching hours per lecturer'),
  ('55555555-5555-5555-5555-555555555502', '11111111-1111-1111-1111-111111111111', 'require_approval', 'true', 'Allocations must be approved before publish'),
  ('55555555-5555-5555-5555-555555555503', '11111111-1111-1111-1111-111111111111', 'allow_cross_department', 'true', 'Allow cross-department teaching requests'),
  ('55555555-5555-5555-5555-555555555504', '11111111-1111-1111-1111-111111111111', 'block_high_conflicts', 'true', 'Block submit when high-severity conflicts remain');

INSERT INTO system_settings (id, tenant_id, setting_key, setting_value, description) VALUES
  ('66666666-6666-6666-6666-666666666601', '11111111-1111-1111-1111-111111111111', 'institution_display_name', 'University of Nairobi', 'Display name in UI'),
  ('66666666-6666-6666-6666-666666666602', '11111111-1111-1111-1111-111111111111', 'default_academic_year', '2026/2027', 'Default academic year label'),
  ('66666666-6666-6666-6666-666666666603', '11111111-1111-1111-1111-111111111111', 'default_semester', 'Semester 1', 'Default semester name'),
  ('66666666-6666-6666-6666-666666666604', '11111111-1111-1111-1111-111111111111', 'timezone', 'Africa/Nairobi', 'Institution timezone');

INSERT INTO import_mapping_profiles (id, tenant_id, name, entity_type, column_map) VALUES
  ('77777777-7777-7777-7777-777777777701', '11111111-1111-1111-1111-111111111111', 'Lecturer CSV (UoN)', 'LECTURER',
   'Staff No=staffNumber;Staff Name=name;Department=organizationNode;Email=email;Max Hours=maximumWorkload'),
  ('77777777-7777-7777-7777-777777777702', '11111111-1111-1111-1111-111111111111', 'Unit CSV (UoN)', 'ACADEMIC_UNIT',
   'Course Code=code;Course Name=name;Department=organizationNode;Hours=contactHours;Students=studentCount');

INSERT INTO semesters (id, tenant_id, academic_year_id, name, sequence_no) VALUES
  ('22222222-2222-2222-2222-222222222212', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222201', 'Semester 2', 2)
ON CONFLICT (academic_year_id, name) DO NOTHING;
