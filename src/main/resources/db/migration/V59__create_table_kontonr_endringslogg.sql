CREATE TABLE IF NOT EXISTS kontonr_endringslogg_v1
(
    id                BIGSERIAL NOT NULL PRIMARY KEY,
    orgnr             ORGNR     NOT NULL REFERENCES virksomhet_v1 (orgnr),
    fnr_oppdatert_av  FNR       NOT NULL,
    kontonr           KONTONR   NOT NULL,
    opprettet         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Legg inn en første rad for dagens avtaler hvor man ikke har hatt endringer....
INSERT INTO kontonr_endringslogg_v1 (orgnr, fnr_oppdatert_av, kontonr, opprettet) SELECT orgnr, fnr_innsender, kontonr, opprettet FROM virksomhet_v1 WHERE opprettet = oppdatert;

-- Setter et utgangspunkt for virksomheter som har vært endret siden opprettelse, og lagrer hva kontonr har vært siden da,
-- men vi kan ikke være sikre på at det var kontonr og ikke e-postadressen som ble endret den dagen. Vi vet heller ikke
-- historien til kontonr'et før den datoen.
INSERT INTO kontonr_endringslogg_v1 (orgnr, fnr_oppdatert_av, kontonr, opprettet) SELECT orgnr, fnr_oppdatert_av, kontonr, oppdatert FROM virksomhet_v1 WHERE opprettet <> oppdatert;

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
