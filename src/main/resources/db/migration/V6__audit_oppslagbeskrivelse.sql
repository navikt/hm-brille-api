ALTER TABLE audit_v1
    ADD COLUMN IF NOT EXISTS oppslag_beskrivelse text NOT NULL default 'hent-bruker - brukeroppslag mot PDL';
ALTER TABLE audit_v1
    ALTER COLUMN id TYPE bigint;
