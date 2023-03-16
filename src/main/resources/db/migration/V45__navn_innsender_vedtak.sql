ALTER TABLE vedtak_v1
    ADD COLUMN navn_innsender TEXT NOT NULL DEFAULT '<Ukjent>';

ALTER TABLE vedtak_slettet_v1
    ADD COLUMN navn_innsender TEXT NOT NULL DEFAULT '<Ukjent>';
