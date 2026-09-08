-- Institution-agnostic import: session results + richer mapping profiles (no dynamic per-institution schemas)

ALTER TABLE import_sessions
    ADD COLUMN IF NOT EXISTS result_summary TEXT;

ALTER TABLE import_mapping_profiles
    ADD COLUMN IF NOT EXISTS mapping_version INT NOT NULL DEFAULT 1;

ALTER TABLE import_mapping_profiles
    ADD COLUMN IF NOT EXISTS file_format VARCHAR(40);

ALTER TABLE import_mapping_profiles
    ADD COLUMN IF NOT EXISTS notes TEXT;

-- Seed a generic ALLOCATION profile template for the demo tenant (headers are examples; engine still remaps)
INSERT INTO import_mapping_profiles (id, tenant_id, name, entity_type, column_map, mapping_version, file_format, notes)
VALUES (
    '88888888-8888-8888-8888-888888888801',
    '11111111-1111-1111-1111-111111111111',
    'Generic allocation (Staff + Course)',
    'ALLOCATION',
    'Staff No.=staffNumber;Lecturer Name=lecturerName;Course Code=unitCode;Course Title=unitName;Hrs=contactHours;Semester=semester',
    1,
    'csv',
    'Reusable starting point for lecturer-to-unit allocation files'
) ON CONFLICT (id) DO NOTHING;
