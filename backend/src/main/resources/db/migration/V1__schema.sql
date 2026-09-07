-- AcademicFlow core schema (multi-tenant via tenant_id)

CREATE TABLE tenants (
    id              UUID PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    code            VARCHAR(50)  NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE users (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    email               VARCHAR(255) NOT NULL,
    full_name           VARCHAR(200) NOT NULL,
    role                VARCHAR(40)  NOT NULL,
    organization_node_id UUID,
    password_hash       VARCHAR(255),
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, email)
);

CREATE TABLE organization_nodes (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    name        VARCHAR(200) NOT NULL,
    type        VARCHAR(80)  NOT NULL,
    parent_id   UUID REFERENCES organization_nodes(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_org_tenant ON organization_nodes(tenant_id);
CREATE INDEX idx_org_parent ON organization_nodes(parent_id);

ALTER TABLE users
    ADD CONSTRAINT fk_users_org
    FOREIGN KEY (organization_node_id) REFERENCES organization_nodes(id);

CREATE TABLE academic_years (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    label       VARCHAR(40) NOT NULL,
    start_date  DATE,
    end_date    DATE,
    UNIQUE (tenant_id, label)
);

CREATE TABLE semesters (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL REFERENCES tenants(id),
    academic_year_id UUID NOT NULL REFERENCES academic_years(id),
    name             VARCHAR(40) NOT NULL,
    sequence_no      INT NOT NULL DEFAULT 1,
    UNIQUE (academic_year_id, name)
);

CREATE TABLE lecturers (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    staff_number        VARCHAR(50)  NOT NULL,
    full_name           VARCHAR(200) NOT NULL,
    email               VARCHAR(255) NOT NULL,
    organization_node_id UUID NOT NULL REFERENCES organization_nodes(id),
    qualifications      VARCHAR(500),
    current_workload    NUMERIC(6,2) NOT NULL DEFAULT 0,
    maximum_workload    NUMERIC(6,2) NOT NULL DEFAULT 12,
    availability_text   VARCHAR(255),
    status              VARCHAR(40) NOT NULL DEFAULT 'Active',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, staff_number)
);
CREATE INDEX idx_lecturer_tenant ON lecturers(tenant_id);
CREATE INDEX idx_lecturer_org ON lecturers(organization_node_id);

CREATE TABLE lecturer_expertise (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL REFERENCES tenants(id),
    lecturer_id  UUID NOT NULL REFERENCES lecturers(id) ON DELETE CASCADE,
    subject      VARCHAR(200) NOT NULL,
    level        VARCHAR(40)  NOT NULL,
    UNIQUE (lecturer_id, subject)
);

CREATE TABLE academic_units (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL REFERENCES tenants(id),
    code                 VARCHAR(40)  NOT NULL,
    name                 VARCHAR(255) NOT NULL,
    source_department_id UUID NOT NULL REFERENCES organization_nodes(id),
    contact_hours        NUMERIC(6,2) NOT NULL DEFAULT 3,
    student_count        INT NOT NULL DEFAULT 0,
    academic_year_id     UUID REFERENCES academic_years(id),
    semester_id          UUID REFERENCES semesters(id),
    required_expertise   TEXT,
    status               VARCHAR(40) NOT NULL DEFAULT 'Unallocated',
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, code, academic_year_id, semester_id)
);
CREATE INDEX idx_unit_tenant ON academic_units(tenant_id);
CREATE INDEX idx_unit_dept ON academic_units(source_department_id);

CREATE TABLE class_groups (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL REFERENCES tenants(id),
    academic_unit_id UUID NOT NULL REFERENCES academic_units(id),
    name             VARCHAR(120) NOT NULL,
    student_count    INT NOT NULL DEFAULT 0
);

CREATE TABLE teaching_requests (
    id                        UUID PRIMARY KEY,
    tenant_id                 UUID NOT NULL REFERENCES tenants(id),
    requesting_department_id  UUID NOT NULL REFERENCES organization_nodes(id),
    preferred_department_id   UUID REFERENCES organization_nodes(id),
    academic_unit_id          UUID NOT NULL REFERENCES academic_units(id),
    student_count             INT NOT NULL,
    contact_hours             NUMERIC(6,2) NOT NULL,
    required_expertise        VARCHAR(255),
    academic_year_id          UUID REFERENCES academic_years(id),
    semester_id               UUID REFERENCES semesters(id),
    status                    VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    created_by                UUID REFERENCES users(id),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_request_tenant ON teaching_requests(tenant_id);
CREATE INDEX idx_request_status ON teaching_requests(status);

CREATE TABLE request_candidates (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL REFERENCES tenants(id),
    teaching_request_id  UUID NOT NULL REFERENCES teaching_requests(id) ON DELETE CASCADE,
    lecturer_id          UUID NOT NULL REFERENCES lecturers(id),
    match_score          NUMERIC(5,2) NOT NULL,
    expertise_score      NUMERIC(5,2) NOT NULL,
    availability_score   NUMERIC(5,2) NOT NULL,
    workload_score       NUMERIC(5,2) NOT NULL,
    student_load_score   NUMERIC(5,2) NOT NULL,
    policy_score         NUMERIC(5,2) NOT NULL,
    qualified            BOOLEAN NOT NULL DEFAULT FALSE,
    available            BOOLEAN NOT NULL DEFAULT FALSE,
    workload_ok          BOOLEAN NOT NULL DEFAULT FALSE,
    no_conflict          BOOLEAN NOT NULL DEFAULT FALSE,
    policy_ok            BOOLEAN NOT NULL DEFAULT FALSE,
    reasons              TEXT,
    warning              TEXT,
    rank_no              INT NOT NULL DEFAULT 0,
    UNIQUE (teaching_request_id, lecturer_id)
);

CREATE TABLE allocations (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL REFERENCES tenants(id),
    academic_unit_id     UUID NOT NULL REFERENCES academic_units(id),
    lecturer_id          UUID REFERENCES lecturers(id),
    teaching_request_id  UUID REFERENCES teaching_requests(id),
    match_score          NUMERIC(5,2),
    status               VARCHAR(40) NOT NULL DEFAULT 'ASSIGNED',
    override_reason      TEXT,
    created_by           UUID REFERENCES users(id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_alloc_tenant ON allocations(tenant_id);
CREATE INDEX idx_alloc_status ON allocations(status);

CREATE TABLE approvals (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL REFERENCES tenants(id),
    allocation_id  UUID NOT NULL REFERENCES allocations(id),
    status         VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    submitted_by   UUID REFERENCES users(id),
    decided_by     UUID REFERENCES users(id),
    decision_note  TEXT,
    submitted_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    decided_at     TIMESTAMPTZ
);

CREATE TABLE timetable_entries (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL REFERENCES tenants(id),
    allocation_id    UUID REFERENCES allocations(id),
    lecturer_id      UUID REFERENCES lecturers(id),
    academic_unit_id UUID REFERENCES academic_units(id),
    class_group_id   UUID REFERENCES class_groups(id),
    day_of_week      INT NOT NULL,
    start_time       TIME NOT NULL,
    end_time         TIME NOT NULL,
    room             VARCHAR(80)
);
CREATE INDEX idx_tt_lecturer ON timetable_entries(lecturer_id);
CREATE INDEX idx_tt_day ON timetable_entries(day_of_week);

CREATE TABLE conflicts (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL REFERENCES tenants(id),
    allocation_id  UUID REFERENCES allocations(id),
    category       VARCHAR(40) NOT NULL,
    severity       VARCHAR(20) NOT NULL,
    description    TEXT NOT NULL,
    related_entity VARCHAR(120),
    resolved       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_logs (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL REFERENCES tenants(id),
    actor_id     UUID REFERENCES users(id),
    action       VARCHAR(120) NOT NULL,
    entity_type  VARCHAR(80)  NOT NULL,
    entity_id    VARCHAR(80),
    details      TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE import_sessions (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL REFERENCES tenants(id),
    file_name     VARCHAR(255) NOT NULL,
    entity_type   VARCHAR(80)  NOT NULL,
    status        VARCHAR(40)  NOT NULL DEFAULT 'UPLOADED',
    column_map    TEXT,
    created_by    UUID REFERENCES users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE import_staging_rows (
    id          UUID PRIMARY KEY,
    session_id  UUID NOT NULL REFERENCES import_sessions(id) ON DELETE CASCADE,
    row_number  INT NOT NULL,
    raw_json    TEXT NOT NULL,
    valid       BOOLEAN NOT NULL DEFAULT TRUE,
    errors      TEXT
);
