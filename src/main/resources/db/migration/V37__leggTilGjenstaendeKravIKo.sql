INSERT INTO vedtak_ko_v1(id, opprettet)
SELECT v.id, v.opprettet
FROM vedtak_v1 v
WHERE NOT EXISTS(
        SELECT 1 FROM utbetaling_v1 u WHERE u.vedtak_id = v.id
    )
  AND NOT EXISTS(
        SELECT 1 FROM vedtak_ko_v1 k WHERE k.id = v.id
    );