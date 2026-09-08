-- Document ingestion metadata for course outlines.
-- Preserves originals; separates upload success from extraction success.
-- Does not alter allocation import tables.

ALTER TABLE course_outlines
    ADD COLUMN IF NOT EXISTS detected_content_type VARCHAR(120);

ALTER TABLE course_outlines
    ADD COLUMN IF NOT EXISTS file_extension VARCHAR(20);

ALTER TABLE course_outlines
    ADD COLUMN IF NOT EXISTS file_size_bytes BIGINT;

ALTER TABLE course_outlines
    ADD COLUMN IF NOT EXISTS checksum_sha256 VARCHAR(64);

ALTER TABLE course_outlines
    ADD COLUMN IF NOT EXISTS processing_status VARCHAR(40) NOT NULL DEFAULT 'UPLOADED';

ALTER TABLE course_outlines
    ADD COLUMN IF NOT EXISTS extraction_method VARCHAR(60);

ALTER TABLE course_outlines
    ADD COLUMN IF NOT EXISTS processing_message TEXT;

ALTER TABLE course_outlines
    ADD COLUMN IF NOT EXISTS processing_error_code VARCHAR(60);

ALTER TABLE course_outlines
    ADD COLUMN IF NOT EXISTS raw_extraction_json TEXT;

CREATE INDEX IF NOT EXISTS idx_course_outlines_status ON course_outlines(tenant_id, processing_status);
