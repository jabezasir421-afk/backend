-- V22: Migrate customer profile photo from avatar_url string to profile_photo_file_id UUID reference
-- 1. Add the profile_photo_file_id column
ALTER TABLE customer
    ADD COLUMN profile_photo_file_id UUID;

-- 2. Migrate existing non-null avatar_url values to stored_file records
INSERT INTO stored_file (
    id,
    owner_user_id,
    entity_type,
    entity_id,
    file_category,
    storage_key,
    original_name,
    mime_type,
    size_bytes,
    active,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    c.user_account_id,
    'CUSTOMER',
    c.id,
    'PROFILE_PHOTO',
    CASE
        WHEN c.avatar_url LIKE 'legacy-avatar/%' THEN c.avatar_url
        WHEN c.avatar_url LIKE 'http%' THEN c.avatar_url
        ELSE 'legacy-avatar/' || CAST(c.id AS VARCHAR(36)) || '/' || c.avatar_url
    END,
    'avatar.jpg',
    'image/jpeg',
    1024,
    TRUE,
    c.created_at,
    c.updated_at
FROM customer c
WHERE c.avatar_url IS NOT NULL AND TRIM(c.avatar_url) <> '';

-- 3. Link customer.profile_photo_file_id to the created stored_file records
UPDATE customer c
SET profile_photo_file_id = (
    SELECT sf.id
    FROM stored_file sf
    WHERE sf.entity_type = 'CUSTOMER'
      AND sf.entity_id = c.id
      AND sf.file_category = 'PROFILE_PHOTO'
    LIMIT 1
)
WHERE c.avatar_url IS NOT NULL AND TRIM(c.avatar_url) <> '';

-- 4. Add foreign key constraint from customer.profile_photo_file_id to stored_file(id)
ALTER TABLE customer
    ADD CONSTRAINT fk_customer_profile_photo
        FOREIGN KEY (profile_photo_file_id) REFERENCES stored_file (id);

-- 5. Add index on customer(profile_photo_file_id)
CREATE INDEX IF NOT EXISTS customer_profile_photo_idx
    ON customer (profile_photo_file_id) WHERE profile_photo_file_id IS NOT NULL;

-- 6. Drop the old avatar_url column safely after migration
ALTER TABLE customer
    DROP COLUMN avatar_url;
