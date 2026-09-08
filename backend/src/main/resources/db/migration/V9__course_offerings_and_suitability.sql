-- Course offering / context + outlines + requirements + allocation decision audit
-- Extends canonical model; does NOT replace AcademicUnit or allocation import.

CREATE TABLE course_offerings (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    academic_unit_id UUID NOT NULL REFERENCES academic_units(id),
    programme VARCHAR(200),
    context_label VARCHAR(120),
    level_label VARCHAR(80),
    display_title VARCHAR(300),
    academic_year_id UUID REFERENCES academic_years(id),
    semester_id UUID REFERENCES semesters(id),
    requesting_department_id UUID REFERENCES organization_nodes(id),
    owning_department_id UUID REFERENCES organization_nodes(id),
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_course_offerings_tenant ON course_offerings(tenant_id);
CREATE INDEX idx_course_offerings_unit ON course_offerings(academic_unit_id);

CREATE TABLE course_outlines (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    course_offering_id UUID NOT NULL REFERENCES course_offerings(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120),
    file_bytes BYTEA,
    extracted_text TEXT,
    extracted_json TEXT,
    extraction_confidence NUMERIC(5,2) NOT NULL DEFAULT 0,
    needs_review BOOLEAN NOT NULL DEFAULT TRUE,
    version_no INT NOT NULL DEFAULT 1,
    uploaded_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_course_outlines_offering ON course_outlines(course_offering_id);

CREATE TABLE course_requirements (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    course_offering_id UUID NOT NULL REFERENCES course_offerings(id) ON DELETE CASCADE,
    requirement_type VARCHAR(40) NOT NULL,
    label VARCHAR(300) NOT NULL,
    weight NUMERIC(6,2) NOT NULL DEFAULT 1,
    source VARCHAR(40) NOT NULL DEFAULT 'MANUAL',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_course_requirements_offering ON course_requirements(course_offering_id);

CREATE TABLE lecturer_context_experience (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    lecturer_id UUID NOT NULL REFERENCES lecturers(id) ON DELETE CASCADE,
    context_label VARCHAR(120),
    programme VARCHAR(200),
    level_label VARCHAR(80),
    topic_label VARCHAR(200),
    unit_code VARCHAR(40),
    times_taught INT NOT NULL DEFAULT 1,
    last_taught_label VARCHAR(80),
    UNIQUE (tenant_id, lecturer_id, context_label, programme, topic_label, unit_code)
);

ALTER TABLE teaching_requests
    ADD COLUMN IF NOT EXISTS course_offering_id UUID REFERENCES course_offerings(id);

ALTER TABLE allocations
    ADD COLUMN IF NOT EXISTS course_offering_id UUID REFERENCES course_offerings(id);

ALTER TABLE allocations
    ADD COLUMN IF NOT EXISTS recommended_lecturer_id UUID REFERENCES lecturers(id);

ALTER TABLE allocations
    ADD COLUMN IF NOT EXISTS suitability_score NUMERIC(5,2);

ALTER TABLE allocations
    ADD COLUMN IF NOT EXISTS suitability_breakdown TEXT;

ALTER TABLE allocations
    ADD COLUMN IF NOT EXISTS decision_type VARCHAR(40);

ALTER TABLE allocations
    ADD COLUMN IF NOT EXISTS decision_note TEXT;

-- Seed demo offerings for MAT 210 Calculus II (Mathematics-owned, cross-dept requests)
INSERT INTO course_offerings (
    id, tenant_id, academic_unit_id, programme, context_label, level_label, display_title,
    requesting_department_id, owning_department_id, status
)
SELECT
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01',
    u.tenant_id,
    u.id,
    'BSc Information Technology',
    'Computing',
    'Year 2',
    'Calculus II for Computing',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa8',
    'ACTIVE'
FROM academic_units u
WHERE u.id = 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee4'
ON CONFLICT (id) DO NOTHING;

INSERT INTO course_offerings (
    id, tenant_id, academic_unit_id, programme, context_label, level_label, display_title,
    requesting_department_id, owning_department_id, status
)
SELECT
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa02',
    u.tenant_id,
    u.id,
    'Bachelor of Commerce',
    'Business',
    'Year 1',
    'Business Calculus',
    NULL,
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa8',
    'ACTIVE'
FROM academic_units u
WHERE u.id = 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee4'
ON CONFLICT (id) DO NOTHING;

INSERT INTO course_requirements (id, tenant_id, course_offering_id, requirement_type, label, weight, source)
VALUES
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01', 'TOPIC', 'Differentiation', 1, 'MANUAL'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb02', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01', 'TOPIC', 'Integration', 1, 'MANUAL'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb03', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01', 'TOPIC', 'Numerical methods', 1, 'MANUAL'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb04', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01', 'TOPIC', 'Computing applications', 1, 'MANUAL'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb05', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01', 'EXPERTISE', 'Calculus', 1, 'MANUAL'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb06', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01', 'EXPERTISE', 'Numerical Methods', 1, 'MANUAL'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb11', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa02', 'TOPIC', 'Limits', 1, 'MANUAL'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb12', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa02', 'TOPIC', 'Differentiation', 1, 'MANUAL'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb13', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa02', 'TOPIC', 'Optimization', 1, 'MANUAL'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb14', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa02', 'TOPIC', 'Financial applications', 1, 'MANUAL'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb15', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa02', 'EXPERTISE', 'Business Mathematics', 1, 'MANUAL'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb16', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa02', 'EXPERTISE', 'Calculus', 1, 'MANUAL')
ON CONFLICT (id) DO NOTHING;

-- Dr. Jane Wanjiku: Computing calculus experience
INSERT INTO lecturer_context_experience (id, tenant_id, lecturer_id, context_label, programme, level_label, topic_label, unit_code, times_taught, last_taught_label)
VALUES
('cccccccc-cccc-cccc-cccc-cccccccccc01', '11111111-1111-1111-1111-111111111111', 'cccccccc-cccc-cccc-cccc-ccccccccccc1', 'Computing', 'BSc Information Technology', 'Year 2', 'Integration', 'MAT 210', 3, '2025/2026'),
('cccccccc-cccc-cccc-cccc-cccccccccc03', '11111111-1111-1111-1111-111111111111', 'cccccccc-cccc-cccc-cccc-ccccccccccc1', 'Computing', 'BSc Information Technology', 'Year 2', 'Numerical methods', 'MAT 210', 2, '2025/2026'),
('cccccccc-cccc-cccc-cccc-cccccccccc02', '11111111-1111-1111-1111-111111111111', 'cccccccc-cccc-cccc-cccc-ccccccccccc2', 'Business', 'Bachelor of Commerce', 'Year 1', 'Optimization', 'MAT 210', 4, '2025/2026'),
('cccccccc-cccc-cccc-cccc-cccccccccc04', '11111111-1111-1111-1111-111111111111', 'cccccccc-cccc-cccc-cccc-ccccccccccc2', 'Business', 'Bachelor of Commerce', 'Year 1', 'Financial applications', 'MAT 210', 3, '2024/2025')
ON CONFLICT DO NOTHING;
