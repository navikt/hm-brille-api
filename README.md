# hm-brille-api

Back-end som håndterer innsending/sletting av krav om refusjon av briller til barn, automatisk vilkårsprøving og
forberedelse til utbetaling av krav.

## Om appen

Denne applikasjonen er den sentrale back-enden for håndtering av brillestøtte til barn i NAV.

Applikasjonen har endepunkter for å:

- opprette, hente og slette brillekrav.
- opprette avtaler for innsending av krav via nav.no og godta bruksvilkår for bruk av API.
- hente rapportdata for innsendte krav.
- hente sats for brillestøtte.

Applikasjonen har klienter mot:

- Altinn for å sjekke om en bedrift har ingått avtale/bruksvilkår
- Altinn for å sjekke om innlogget person har rettigheter for å inngå avtale/bruksvilkår eller å se rapportdata.
- Helsepersonellregisteret for å sjekke om innlogget person er autorisert optiker

### Overordnet dokumentasjon

Overordnet dokumentasjon av brillestøtte finnes [her](https://github.com/navikt/hm-brille/blob/main/doc/%C3%98kosystem.md)

### Henvendelser

Spørsmål knyttet til koden eller prosjektet kan rettes mot https://github.com/orgs/navikt/teams/teamdigihot

### Slack

- `#digihot`
- `#digihot-teknisk`

## Lokal logback

I IntelliJ -> VM-options: `-Dlogback.configurationFile=src/test/resources/logback-local.xml`


