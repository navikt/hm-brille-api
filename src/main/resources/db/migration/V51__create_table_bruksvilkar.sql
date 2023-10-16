
CREATE TABLE IF NOT EXISTS bruksvilkardefinisjon_v1
(
    id                 BIGSERIAL NOT NULL PRIMARY KEY,
    bruksvilkarnavn    TEXT,
    versjon            SERIAL
);

INSERT INTO bruksvilkardefinisjon_v1(id, bruksvilkarnavn, versjon)
VALUES (1, 'Bruksvilkår - Integrasjon for brillekravregistrering', 1);


CREATE TABLE IF NOT EXISTS bruksvilkar_v1
(
    id             BIGSERIAL NOT NULL PRIMARY KEY,
    orgnr          ORGNR     NOT NULL REFERENCES virksomhet_v1 (orgnr),
    fnr_innsender  FNR       NOT NULL,
    aktiv          BOOLEAN   NOT NULL,
    epost_kontaktperson      TEXT      NULL,
    bruksvilkardefinisjon_id    BIGSERIAL NOT NULL REFERENCES bruksvilkardefinisjon_v1 (id),
    opprettet      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    oppdatert      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
