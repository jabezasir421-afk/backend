-- Migrate customer profile photo from avatar_url string to profile_photo_file_id UUID reference
-- Add the new column
ALTER TABLE customer
    ADD COLUMN profile_photo_file_id UUID;

-- Drop the old avatar_url column
ALTER TABLE customer
    DROP COLUMN avatar_url;
