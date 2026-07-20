-- Older manual-only profiles were created with the adapter fallback as their
-- visible name. Replace that technical placeholder with the owning user's name.
UPDATE entities e
SET display_name = u.display_name,
    updated_at = now()
FROM users u
WHERE e.owner_id = u.id
  AND e.display_name = 'Manual Entry'
  AND u.display_name IS NOT NULL
  AND btrim(u.display_name) <> '';
