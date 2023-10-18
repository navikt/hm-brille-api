package no.nav.hjelpemidler.brille.vilkarsvurdering

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.admin.AdminService
import no.nav.hjelpemidler.brille.audit.AuditService
import no.nav.hjelpemidler.brille.extractFnr
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.pdl.HentPersonExtensions.navn
import no.nav.hjelpemidler.brille.sats.SatsKalkulator
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.nare.evaluering.Resultat

private val sikkerLog = KotlinLogging.logger("tjenestekall")

private val log = KotlinLogging.logger { }
fun Route.vilkårApi(
    vilkårsvurderingService: VilkårsvurderingService,
    adminService: AdminService,
    auditService: AuditService,
    kafkaService: KafkaService,
) {
    post("/vilkarsgrunnlag") {
        try {
            val vilkårsgrunnlag = call.receive<VilkårsgrunnlagDto>()
            auditService.lagreOppslag(
                fnrInnlogget = call.extractFnr(),
                fnrOppslag = vilkårsgrunnlag.fnrBarn,
                oppslagBeskrivelse = "[POST] /vilkarsgrunnlag - Sjekk om barn og bestilling oppfyller vilkår for støtte",
            )
            val vilkarsvurdering = vilkårsvurderingService.vurderVilkår(
                vilkårsgrunnlag.fnrBarn,
                vilkårsgrunnlag.brilleseddel,
                vilkårsgrunnlag.bestillingsdato,
            )
            val sats = when (vilkarsvurdering.utfall) {
                Resultat.JA -> SatsKalkulator(vilkårsgrunnlag.brilleseddel).kalkuler()
                else -> SatsType.INGEN
            }

            if (vilkarsvurdering.utfall != Resultat.JA) {
                sikkerLog.info {
                    "Vilkårsvurderingen ga negativt resultat:\n${vilkarsvurdering.toJson()}"
                }

                kafkaService.vilkårIkkeOppfylt(vilkårsgrunnlag, vilkarsvurdering)

                val årsaker = vilkarsvurdering.evaluering.barn
                    .filter { vilkar -> vilkar.resultat != Resultat.JA }
                    .map { vilkar -> vilkar.begrunnelse }

                // Lagre avvisningsårsaker, hvem og hvorfor. Brukes i brille-admin.
                adminService.lagreAvvisning(vilkårsgrunnlag.fnrBarn, call.extractFnr(), vilkårsgrunnlag.orgnr, årsaker)

                // Journalfør avvisningsbrev i joark
                if (!adminService.harAvvisningDeSiste7DageneFor(
                        vilkårsgrunnlag.fnrBarn,
                        vilkårsgrunnlag.orgnr,
                    )
                ) {
                    val årsakerIdentifikator = vilkarsvurdering.evaluering.barn
                        .filter { vilkar -> vilkar.resultat != Resultat.JA }
                        .map { vilkar -> vilkar.identifikator }

                    val eksisterendeVedtakDatoDirekteoppgjor = vilkarsvurdering.grunnlag.vedtakBarn
                        .maxByOrNull { it.opprettet }?.opprettet?.toLocalDate()
                    val eksisterendeVedtakDatoHotsak = vilkarsvurdering.grunnlag.eksisterendeVedtakDatoHotsak
                    val eksisterendeVedtakDato = if (eksisterendeVedtakDatoDirekteoppgjor == null || eksisterendeVedtakDatoHotsak == null) {
                        eksisterendeVedtakDatoDirekteoppgjor ?: eksisterendeVedtakDatoHotsak
                    } else {
                        if (eksisterendeVedtakDatoDirekteoppgjor.isAfter(eksisterendeVedtakDatoHotsak)) {
                            eksisterendeVedtakDatoDirekteoppgjor
                        } else {
                            eksisterendeVedtakDatoHotsak
                        }
                    }

                    kafkaService.journalførAvvisning(
                        vilkårsgrunnlag.fnrBarn,
                        vilkarsvurdering.grunnlag.pdlOppslagBarn.data!!.navn(),
                        vilkårsgrunnlag.orgnr,
                        vilkårsgrunnlag.extras.orgNavn,
                        vilkårsgrunnlag.brilleseddel,
                        vilkårsgrunnlag.bestillingsdato,
                        eksisterendeVedtakDato,
                        årsakerIdentifikator,
                    )
                }
            }

            val beløp = minOf(sats.beløp(vilkårsgrunnlag.bestillingsdato).toBigDecimal(), vilkårsgrunnlag.brillepris)

            val refInnsendersTidligereKrav =
                if (!vilkarsvurdering.harResultatJaForVilkår("HarIkkeVedtakIKalenderåret")) {
                    vilkarsvurdering.grunnlag.vedtakBarn.firstOrNull {
                        it.fnrInnsender == call.extractFnr() && it.bestillingsdato.year == vilkårsgrunnlag.bestillingsdato.year
                    }
                        ?.bestillingsreferanse
                } else {
                    null
                }

            call.respond(
                VilkårsvurderingDto(
                    resultat = vilkarsvurdering.utfall,
                    sats = sats,
                    satsBeskrivelse = sats.beskrivelse,
                    satsBeløp = sats.beløp(vilkårsgrunnlag.bestillingsdato),
                    beløp = beløp,
                    kravFraFørFraInnsender = refInnsendersTidligereKrav,
                ),
            )
        } catch (e: Exception) {
            log.error(e) { "Feil i vilkårsvurdering" }
            call.respond(HttpStatusCode.InternalServerError, "Feil i vilkårsvurdering")
        }
    }
}
