package no.nav.hjelpemidler.brille.vilkarsvurdering

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.hjelpemidler.brille.admin.AdminService
import no.nav.hjelpemidler.brille.audit.AuditService
import no.nav.hjelpemidler.brille.extractFnr
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.pdl.HentPersonExtensions.navn
import no.nav.hjelpemidler.brille.sats.SatsKalkulator
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.tid.toLocalDate
import no.nav.hjelpemidler.brille.vedtak.EksisterendeVedtak
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.logging.teamInfo
import no.nav.hjelpemidler.nare.regel.Regelutfall

private val log = KotlinLogging.logger {}

fun Route.vilkårApi(
    vilkårsvurderingService: VilkårsvurderingService,
    adminService: AdminService,
    auditService: AuditService,
    kafkaService: KafkaService,
) {
    post("/vilkarsgrunnlag") {
        try {
            val vilkårsgrunnlag = call.receive<VilkårsgrunnlagDto>()
            if (!vilkårsgrunnlag.validerBestillingsdatoIkkeIFremtiden()) {
                return@post call.respond(HttpStatusCode.BadRequest, "bestillingsdato kan ikke være i fremtiden")
            }

            auditService.lagreOppslag(
                fnrInnlogget = call.extractFnr(),
                fnrOppslag = vilkårsgrunnlag.fnrBarn,
                oppslagBeskrivelse = "[POST] /vilkarsgrunnlag - Sjekk om barn og bestilling oppfyller vilkår for støtte",
            )
            val vilkårsvurdering = vilkårsvurderingService.vurderVilkår(
                vilkårsgrunnlag.fnrBarn,
                vilkårsgrunnlag.brilleseddel,
                vilkårsgrunnlag.bestillingsdato,
            )
            val sats = when (vilkårsvurdering.utfall) {
                Regelutfall.JA -> SatsKalkulator(vilkårsgrunnlag.brilleseddel).kalkuler()
                else -> SatsType.INGEN
            }

            if (vilkårsvurdering.utfall != Regelutfall.JA) {
                log.teamInfo {
                    "Vilkårsvurderingen ga negativt resultat:\n${vilkårsvurdering.toJson()}"
                }

                kafkaService.vilkårIkkeOppfylt(vilkårsgrunnlag, vilkårsvurdering)

                val årsaker = vilkårsvurdering.evaluering.barn
                    .filter { vilkar -> vilkar.resultat != Regelutfall.JA }
                    .map { vilkar -> vilkar.begrunnelse }

                val haddeAvvisningsbrevFraFør = adminService.harAvvisningDeSiste7DageneFor(
                    vilkårsgrunnlag.fnrBarn,
                    vilkårsgrunnlag.orgnr,
                )

                // Lagre avvisningsårsaker, hvem og hvorfor. Brukes i brille-admin.
                adminService.lagreAvvisning(
                    vilkårsgrunnlag.fnrBarn,
                    call.extractFnr(),
                    vilkårsgrunnlag.orgnr,
                    vilkårsgrunnlag.butikkId,
                    årsaker,
                )

                // Journalfør avvisningsbrev i joark
                if (haddeAvvisningsbrevFraFør && !Environment.current.isDev) {
                    log.info { "Avviser vilkårsvurdering men sender ikke avvisningsbrev pga. tidligere brev sendt de siste 7 dagene" }
                    kafkaService.sendteIkkeAvvisningsbrevPgaTidligereBrev7Dager("krav_app")
                } else {
                    val årsakerIdentifikator = vilkårsvurdering.evaluering.barn
                        .filter { vilkar -> vilkar.resultat != Regelutfall.JA }
                        .map { vilkar -> vilkar.id }

                    val eksisterendeVedtakDato =
                        vilkårsvurdering.grunnlag.senesteVedtak()?.vedtaksdato?.toLocalDate()

                    kafkaService.journalførAvvisning(
                        vilkårsgrunnlag.fnrBarn,
                        vilkårsvurdering.grunnlag.pdlOppslagBarn.data!!.navn(),
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
                if (!vilkårsvurdering.harResultatJaForVilkår("HarIkkeVedtakIKalenderåret")) {
                    vilkårsvurdering.grunnlag.vedtakBarn.filterIsInstance<EksisterendeVedtak>().firstOrNull {
                        it.fnrInnsender == call.extractFnr() && it.bestillingsdato.year == vilkårsgrunnlag.bestillingsdato.year
                    }
                        ?.bestillingsreferanse
                } else {
                    null
                }

            call.respond(
                VilkårsvurderingDto(
                    resultat = vilkårsvurdering.utfall,
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
