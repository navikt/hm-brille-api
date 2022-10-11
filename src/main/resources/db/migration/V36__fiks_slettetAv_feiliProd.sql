UPDATE vedtak_slettet_v1
SET slettet_av = fnr_innsender
WHERE slettet_av IS NULL
;

UPDATE vedtak_slettet_v1
SET slettet_av = 'manuelt slettet'
WHERE slettet_av = '[manuelt slettet]'
;
