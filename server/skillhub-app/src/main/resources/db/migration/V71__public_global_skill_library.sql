-- Global is the public skill library. Team skills and private drafts retain their visibility.
UPDATE skill s
SET visibility = 'PUBLIC'
FROM namespace n
WHERE s.namespace_id = n.id
  AND n.type = 'GLOBAL'
  AND s.visibility = 'NAMESPACE_ONLY'
  AND s.status = 'ACTIVE'
  AND s.hidden = FALSE
  AND s.latest_version_id IS NOT NULL;

-- Keep discovery consistent with the authoritative skill visibility.
UPDATE skill_search_document d
SET visibility = s.visibility
FROM skill s, namespace n
WHERE d.skill_id = s.id
  AND s.namespace_id = n.id
  AND n.type = 'GLOBAL'
  AND s.visibility = 'PUBLIC';
