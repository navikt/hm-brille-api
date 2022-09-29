ALTER TABLE vedtak_slettet_v1
    ADD COLUMN slettet_av FNR,
    ADD COLUMN slettet_av_type TEXT DEFAULT 'INNSENDER'
;

UPDATE vedtak_slettet_v1
SET slettet_av = fnr_innsender
WHERE slettet_av IS NULL
;