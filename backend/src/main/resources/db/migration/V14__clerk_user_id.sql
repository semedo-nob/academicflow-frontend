-- Link AcademicFlow users to Clerk identities (stable user id, not email)

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS clerk_user_id VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_clerk_user_id
    ON users (clerk_user_id)
    WHERE clerk_user_id IS NOT NULL;
