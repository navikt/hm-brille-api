-- 916164548 was fusioned into 929880870 and deleted. Old org deactivated in pgadmin.
UPDATE vedtak_v1
SET orgnr = '929880870'
WHERE id IN (57926, 57179) AND orgnr = '916164548'
