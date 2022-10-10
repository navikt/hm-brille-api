ALTER TABLE vedtak_slettet_v1
    ALTER COLUMN slettet_av SET DATA TYPE TEXT,
    ALTER COLUMN slettet_av SET NOT NULL,
    ALTER COLUMN slettet_av_type SET NOT NULL
;
