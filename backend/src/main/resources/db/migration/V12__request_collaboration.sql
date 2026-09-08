-- Cross-department teaching request collaboration:
-- attach course outlines / docs, and bidirectional department messaging.

ALTER TABLE teaching_requests
    ADD COLUMN IF NOT EXISTS briefing_note TEXT;

CREATE TABLE request_attachments (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    teaching_request_id UUID NOT NULL REFERENCES teaching_requests(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120),
    file_size_bytes BIGINT,
    file_bytes BYTEA,
    doc_type VARCHAR(40) NOT NULL DEFAULT 'COURSE_OUTLINE',
    uploaded_by UUID,
    uploaded_by_name VARCHAR(200),
    department_id UUID REFERENCES organization_nodes(id),
    department_name VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_request_attachments_request ON request_attachments(teaching_request_id);
CREATE INDEX idx_request_attachments_tenant ON request_attachments(tenant_id);

CREATE TABLE request_messages (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    teaching_request_id UUID NOT NULL REFERENCES teaching_requests(id) ON DELETE CASCADE,
    author_user_id UUID,
    author_name VARCHAR(200),
    author_role VARCHAR(60),
    author_department_id UUID REFERENCES organization_nodes(id),
    author_department_name VARCHAR(200),
    message_type VARCHAR(40) NOT NULL DEFAULT 'COMMENT',
    body TEXT NOT NULL,
    related_lecturer_id UUID REFERENCES lecturers(id),
    related_lecturer_name VARCHAR(200),
    notify_authority BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_request_messages_request ON request_messages(teaching_request_id);
CREATE INDEX idx_request_messages_tenant ON request_messages(tenant_id);
CREATE INDEX idx_request_messages_created ON request_messages(teaching_request_id, created_at);
