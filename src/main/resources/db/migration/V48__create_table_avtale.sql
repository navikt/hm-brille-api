
CREATE TABLE IF NOT EXISTS avtaledefinisjon_v1
(
    id             BIGSERIAL NOT NULL PRIMARY KEY,
    avtalenavn     TEXT,
    versjon        SERIAL
);

INSERT INTO avtaledefinisjon_v1(id, avtalenavn, versjon)
VALUES (1, 'Avtale om direkte oppgjør av briller for barn', 1);

INSERT INTO avtaledefinisjon_v1(id, avtalenavn, versjon)
VALUES (2, 'Utvid avtale for å sende brillekrav via eget system', 1);

CREATE TABLE IF NOT EXISTS avtale_v1
(
    id             BIGSERIAL NOT NULL PRIMARY KEY,
    orgnr          ORGNR     NOT NULL REFERENCES virksomhet_v1 (orgnr),
    fnr_innsender  FNR       NOT NULL,
    aktiv          BOOLEAN   NOT NULL,
    avtale_id      BIGSERIAL NOT NULL REFERENCES avtaledefinisjon_v1 (id),
    opprettet      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    oppdatert      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO avtale_v1
(orgnr, fnr_innsender, aktiv, avtale_id, opprettet, oppdatert)
SELECT orgnr,
       fnr_innsender,
       aktiv,
       1,
       opprettet,
       oppdatert
FROM virksomhet_v1;

--todo rydd opp virksomhet_v1

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
