ALTER TABLE vedtak_v2
    DROP CONSTRAINT IF EXISTS gyldig_belop;
ALTER TABLE vedtak_v2
    ADD CONSTRAINT gyldig_belop CHECK (brillepris ~ '^[+-]?(\d+([.]\d*)?|[.]\d+)$');
