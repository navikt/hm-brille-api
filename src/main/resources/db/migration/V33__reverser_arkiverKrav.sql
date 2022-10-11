INSERT INTO vedtak_v1
SELECT id,
       fnr_barn,
       fnr_innsender,
       orgnr,
       bestillingsdato,
       brillepris,
       bestillingsreferanse,
       vilkarsvurdering,
       behandlingsresultat,
       sats,
       sats_belop,
       sats_beskrivelse,
       belop,
       opprettet
FROM vedtak_slettet_v1
WHERE id IN (5021);

DELETE
FROM vedtak_slettet_v1
WHERE id IN (5021);
