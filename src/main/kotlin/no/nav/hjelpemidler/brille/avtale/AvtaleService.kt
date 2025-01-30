package no.nav.hjelpemidler.brille.avtale

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.altinn.ALTINN_CLIENT_MAKS_ANTALL_RESULTATER
import no.nav.hjelpemidler.brille.altinn.AltinnService
import no.nav.hjelpemidler.brille.altinn.Avgiver
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.enhetsregisteret.Næringskode
import no.nav.hjelpemidler.brille.enhetsregisteret.Organisasjonsenhet
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.slack.Slack
import no.nav.hjelpemidler.brille.virksomhet.Virksomhet
import java.time.LocalDateTime
import java.util.UUID
import kotlin.system.measureTimeMillis

private val log = KotlinLogging.logger { }
private val sikkerLog = KotlinLogging.logger("tjenestekall")

class AvtaleService(
    val databaseContext: DatabaseContext,
    private val altinnService: AltinnService,
    private val enhetsregisteretService: EnhetsregisteretService,
    private val kafkaService: KafkaService,
) {
    suspend fun hentOrganisasjonsenhet(orgnr: String): Organisasjonsenhet =
        requireNotNull(enhetsregisteretService.hentOrganisasjonsenhet(orgnr)) {
            "Fant ikke organisasjonsenhet med orgnr: $orgnr"
        }

    suspend fun hentAvtaler(fnr: String, tjeneste: Avgiver.Tjeneste): List<IngåttAvtale> {
        var avgivere: List<Avgiver>
        val hentAvgivereElapsed = measureTimeMillis { avgivere = altinnService.hentAvgivere(fnr = fnr, tjeneste = tjeneste) }
        log.info("hentAvtaler: altinnService.hentAvgivere elapsed=${hentAvgivereElapsed}ms")

        if (avgivere.count() >= ALTINN_CLIENT_MAKS_ANTALL_RESULTATER) {
            val id = UUID.randomUUID()
            sikkerLog.info("Hentet avtaler for en person med flere avgivere i altinn enn vi ber om fra altinn (fnr=$fnr, tjeneste=$tjeneste, id=$id)")
            Slack.post("Hentet avtaler for en person med flere avgivere i altinn enn vi ber om fra altinn (se mer i sikkerlogg med id=$id)")
        }

        var enheter: Map<String, Organisasjonsenhet>
        val hentOrganisasjonsenheterElapsed = measureTimeMillis {
            enheter = enhetsregisteretService.hentOrganisasjonsenheter(avgivere.map { it.orgnr }.toSet())
        }
        log.info("hentAvtaler: enhetsregisteretService.hentOrganisasjonsenheter elapsed=${hentOrganisasjonsenheterElapsed}ms")

        val avgivereFiltrert = avgivere.filter { avgiver ->
            val orgnr = avgiver.orgnr
            val enhet = enheter[orgnr]
            if (enhet == null) {
                false
            } else {
                log.info {
                    "Hentet enhet med orgnr: $orgnr, næringskoder: ${enhet.næringskoder().map { it.kode }}"
                }
                setOf(
                    Næringskode.BUTIKKHANDEL_MED_OPTISKE_ARTIKLER,
                    Næringskode.BUTIKKHANDEL_MED_GULL_OG_SØLVVARER,
                    Næringskode.BUTIKKHANDEL_MED_UR_OG_KLOKKER,
                    Næringskode.BUTIKKHANDEL_MED_HELSEKOST,
                    Næringskode.ANDRE_HELSETJENESTER,
                    Næringskode.ENGROSHANDEL_MED_OPTISKE_ARTIKLER,
                    Næringskode.SPESIALISERT_LEGETJENESTE_UNNTATT_PSYKIATRISK_LEGETJENESTE,
                ).any { enhet.harNæringskode(it) }
            }
        }

        sikkerLog.info {
            "Filtrert avgivere for fnr: $fnr, tjeneste: $tjeneste, avgivere: $avgivereFiltrert"
        }

        var virksomheter: Map<String, Virksomhet>
        val hentVirksomheterForOrganisasjonerElapsed = measureTimeMillis {
            virksomheter = transaction(databaseContext) { ctx ->
                ctx.virksomhetStore.hentVirksomheterForOrganisasjoner(avgivereFiltrert.map { it.orgnr }).associateBy {
                    it.orgnr
                }
            }
        }
        log.info("hentAvtaler: virksomhetStore.hentVirksomheterForOrganisasjoner elapsed=${hentVirksomheterForOrganisasjonerElapsed}ms")

        return avgivereFiltrert
            .map {
                IngåttAvtale(
                    orgnr = it.orgnr,
                    navn = it.navn,
                    aktiv = virksomheter[it.orgnr]?.aktiv ?: false,
                    kontonr = virksomheter[it.orgnr]?.kontonr,
                    epost = virksomheter[it.orgnr]?.epost,
                    avtaleversjon = virksomheter[it.orgnr]?.avtaleversjon,
                    bruksvilkår = virksomheter[it.orgnr]?.bruksvilkår,
                    bruksvilkårOpprettet = virksomheter[it.orgnr]?.bruksvilkårGodtattDato,
                    opprettet = virksomheter[it.orgnr]?.opprettet,
                    oppdatert = virksomheter[it.orgnr]?.oppdatert,
                )
            }
    }

    suspend fun hentAvtale(
        fnr: String,
        orgnr: String,
        tjeneste: Avgiver.Tjeneste,
    ): IngåttAvtale? = hentAvtaler(fnr = fnr, tjeneste = tjeneste).associateBy {
        it.orgnr
    }[orgnr]

    suspend fun opprettAvtale(fnrInnsender: String, opprettAvtale: OpprettAvtale): IngåttAvtale {
        val orgnr = opprettAvtale.orgnr

        if (!altinnService.harTilgangTilOppgjørsavtale(fnrInnsender, orgnr)) {
            throw AvtaleManglerTilgangException(orgnr)
        }

        log.info { "Oppretter avtale for orgnr: $orgnr" }
        sikkerLog.info { "fnrInnsender: $fnrInnsender, opprettAvtale: $opprettAvtale" }

        val virksomhet = transaction(databaseContext) { ctx ->
            val virksomhet = ctx.virksomhetStore.lagreVirksomhet(
                Virksomhet(
                    orgnr = orgnr,
                    kontonr = opprettAvtale.kontonr,
                    epost = opprettAvtale.epost,
                    fnrInnsender = fnrInnsender,
                    navnInnsender = "", // todo -> slett
                    aktiv = true,
                    avtaleversjon = null,
                ),
            )
            ctx.avtaleStore.lagreAvtale(
                Avtale(
                    orgnr = orgnr,
                    fnrInnsender = fnrInnsender,
                    aktiv = true,
                    avtaleId = AVTALETYPE.OPPGJORSAVTALE.avtaleId,
                ),
            )
            virksomhet
        }

        val organisasjonsenhet = hentOrganisasjonsenhet(orgnr)
        val avtale = IngåttAvtale(virksomhet = virksomhet, navn = organisasjonsenhet.navn)

        // For å unngå at gammelt kontonr kan brukes innen nytt er ferdigregistrert i TSS så glemmer vi alle gamle
        // TSS-identer her. Disse vil settes igjen etter TSS har kvittert mottak av nytt kontonr. Se: TssIdentRiver.
        // (finnes vanligvis ikke fra før på nye avtaler)
        transaction(databaseContext) { ctx ->
            ctx.tssIdentStore.glemEksisterendeTssIdent(orgnr)
        }
        kafkaService.avtaleOpprettet(avtale)

        if (Configuration.dev || Configuration.prod) {
            Slack.post(
                "AvtaleService: Ny avtale opprettet for orgnr=$orgnr. Husk å be #po-utbetaling-barnebriller om å legge TSS-ident i listen over identer som ikke skal få oppdrag slått sammen av oppdrag. TSS-ident kan finnes i kibana secureLog (søk: `Kontonr synkronisert til TSS: orgnr=$orgnr`), eller ved å slå opp i database med:" +
                    "```" +
                    "-- Hent ut tss-ident for virksomhet med ny avtale for å sende denne over til\n" +
                    "-- po utbetaling/UR\n" +
                    "SELECT v.orgnr, t.tss_ident, v.oppdatert\n" +
                    "FROM virksomhet_v1 v\n" +
                    "LEFT JOIN tssident_v1 t ON t.orgnr = v.orgnr\n" +
                    "WHERE v.orgnr = '$orgnr'" +
                    "```",
            )
        }

        return avtale
    }

    suspend fun godtaBruksvilkår(
        fnrInnsender: String,
        orgnr: String,
    ): BruksvilkårGodtattDto {
        if (!altinnService.harTilgangTilOppgjørsavtale(fnrInnsender, orgnr)) {
            throw AvtaleManglerTilgangException(orgnr)
        }

        log.info { "Registrerer at bruksvilkår for api er godtatt for orgnr: $orgnr" }
        sikkerLog.info { "fnrInnsender: $fnrInnsender, bruksvilkår for api godtatt for orgnr: $orgnr" }

        val bruksvilkårGodtatt = transaction(databaseContext) { ctx ->
            val bruksvilkårGodtatt = ctx.avtaleStore.godtaBruksvilkår(
                BruksvilkårGodtatt(
                    orgnr = orgnr,
                    fnrInnsender = fnrInnsender,
                    aktiv = true,
                    bruksvilkårDefinisjonId = BRUKSVILKÅRTYPE.BRUKSVILKÅR_API.bruksvilkårId,
                ),
            )
            bruksvilkårGodtatt
        }

        val organisasjonsenhet = hentOrganisasjonsenhet(orgnr)
        kafkaService.bruksvilkårGodtatt(bruksvilkårGodtatt, organisasjonsenhet.navn)

        if (Configuration.dev || Configuration.prod) {
            Slack.post("AvtaleService: Bruksvilkår godtatt for orgnr=$orgnr.")
        }

        return BruksvilkårGodtattDto.fromBruksvilkårGodtatt(bruksvilkårGodtatt, organisasjonsenhet.navn)
    }

    suspend fun deaktiverVirksomhet(orgnr: String) {
        log.info { "Deaktiverer orgnr: $orgnr" }
        transaction(databaseContext) { ctx ->
            ctx.avtaleStore.deaktiverVirksomhet(orgnr)
        }
    }

    suspend fun oppdaterAvtale(fnrOppdatertAv: String, orgnr: String, oppdaterAvtale: OppdaterAvtale): IngåttAvtale {
        if (!altinnService.harTilgangTilOppgjørsavtale(fnrOppdatertAv, orgnr)) {
            throw AvtaleManglerTilgangException(orgnr)
        }

        log.info { "Oppdaterer avtale for orgnr: $orgnr" }
        sikkerLog.info { "fnrOppdatertAv: $fnrOppdatertAv, orgnr: $orgnr, oppdaterAvtale: $oppdaterAvtale" }

        val virksomhet = transaction(databaseContext) { ctx ->
            val virksomhet = ctx.virksomhetStore.oppdaterVirksomhet(
                requireNotNull(ctx.virksomhetStore.hentVirksomhetForOrganisasjon(orgnr)) {
                    "Fant ikke virksomhet med orgnr: $orgnr"
                }.copy(
                    kontonr = oppdaterAvtale.kontonr,
                    epost = oppdaterAvtale.epost,
                    fnrOppdatertAv = fnrOppdatertAv,
                    oppdatert = LocalDateTime.now(),
                ),
            )

            virksomhet
        }

        if (virksomhet.fnrInnsender != virksomhet.fnrOppdatertAv) {
            sikkerLog.warn {
                "Avtalen ble oppdatert av en annen en innsender, fnrInnsender: ${virksomhet.fnrInnsender}, fnrOppdatertAv: ${virksomhet.fnrOppdatertAv}"
            }
        }

        val organisasjonsenhet = hentOrganisasjonsenhet(orgnr)
        val avtale = IngåttAvtale(virksomhet = virksomhet, navn = organisasjonsenhet.navn)

        // For å unngå at gammelt kontonr kan brukes innen nytt er ferdigregistrert i TSS så glemmer vi alle gamle
        // TSS-identer her. Disse vil settes igjen etter TSS har kvittert mottak av nytt kontonr. Se: TssIdentRiver.
        transaction(databaseContext) { ctx ->
            ctx.tssIdentStore.glemEksisterendeTssIdent(orgnr)
        }

        kafkaService.avtaleOppdatert(avtale)

        return avtale
    }
}
