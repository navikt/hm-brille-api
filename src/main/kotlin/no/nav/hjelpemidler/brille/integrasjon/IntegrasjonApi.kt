package no.nav.hjelpemidler.brille.integrasjon

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.audit.AuditService
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.enhetsregisteret.Organisasjonsenhet
import no.nav.hjelpemidler.brille.nare.evaluering.Resultat
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import no.nav.hjelpemidler.brille.sats.SatsKalkulator
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.tilgang.withTilgangContext
import no.nav.hjelpemidler.brille.vedtak.Behandlingsresultat
import no.nav.hjelpemidler.brille.vedtak.KravDto
import no.nav.hjelpemidler.brille.vedtak.VedtakService
import no.nav.hjelpemidler.brille.vedtak.toDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsgrunnlagDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsgrunnlagExtrasDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsvurderingService
import no.nav.hjelpemidler.brille.virksomhet.enhetTilAdresseFor
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Random

private val log = KotlinLogging.logger {}

fun Route.integrasjonApi(
    vilkårsvurderingService: VilkårsvurderingService,
    vedtakService: VedtakService,
    auditService: AuditService,
    enhetsregisteretService: EnhetsregisteretService,
) {
    route("/integrasjon") {
        post("/vilkarsvurdering") {
            data class Request(
                val fnrBarn: String,
                val brilleseddel: Brilleseddel,
                val bestillingsdato: LocalDate,
            )

            data class Response(
                val resultat: Resultat,
                val sats: SatsType,
                val satsBeløp: BigDecimal,
            )

            try {
                val vilkårsgrunnlagInput = call.receive<Request>()
                val vilkarsvurdering = withTilgangContext(call) {
                    vilkårsvurderingService.vurderVilkår(
                        vilkårsgrunnlagInput.fnrBarn,
                        vilkårsgrunnlagInput.brilleseddel,
                        vilkårsgrunnlagInput.bestillingsdato
                    )
                }

                val sats = SatsKalkulator(vilkårsgrunnlagInput.brilleseddel).kalkuler()
                call.respond(
                    Response(
                        resultat = vilkarsvurdering.utfall,
                        sats = sats,
                        satsBeløp = sats.beløp.toBigDecimal(),
                    )
                )
            } catch (e: Exception) {
                log.error(e) { "Feil i vilkårsvurdering" }
                call.respond(HttpStatusCode.InternalServerError, "Feil i vilkårsvurdering")
            }
        }

        post("/krav") {
            data class Request(
                val fnrBarn: String,
                val brilleseddel: Brilleseddel,
                val brillepris: BigDecimal,
                val bestillingsdato: LocalDate,
                val bestillingsreferanse: String,
                val virksomhetOrgnr: String,
                val ansvarligOptikersFnr: String,
            )

            data class Response(
                val resultat: Resultat,
                val sats: SatsType,
                val satsBeløp: BigDecimal,
                val navReferanse: Long,
            )

            try {
                val req = call.receive<Request>()

                // TODO: HPR oppslag, sjekk autorisasjon, hent navn
                val navnInnsender = /*redisClient.optikerNavn(fnrInnsender) ?:*/ "<Ukjent>"

                // Slå opp orgnavn/-adresse fra enhetsregisteret
                val enhet: Organisasjonsenhet = enhetsregisteretService.hentOrganisasjonsenhet(req.virksomhetOrgnr)
                    ?: return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        "Fant ikke organisasjonsenhet for orgnr: $it"
                    )

                // Audit logging
                auditService.lagreOppslag(
                    fnrInnlogget = req.ansvarligOptikersFnr,
                    fnrOppslag = req.fnrBarn,
                    oppslagBeskrivelse = "[POST] /krav - Innsending av krav"
                )

                // Kjør vilkårsvurdering og opprett vedtak
                val vedtak = vedtakService.lagVedtak(req.ansvarligOptikersFnr, navnInnsender, KravDto(
                    vilkårsgrunnlag = VilkårsgrunnlagDto(
                        orgnr = req.virksomhetOrgnr,
                        fnrBarn = req.fnrBarn,
                        brilleseddel = req.brilleseddel,
                        bestillingsdato = req.bestillingsdato,
                        brillepris = req.brillepris,
                        extras = VilkårsgrunnlagExtrasDto(
                            orgNavn = enhet.navn,
                            bestillingsreferanse = req.bestillingsreferanse,
                        ),
                    ),
                    bestillingsreferanse = req.bestillingsreferanse,
                    brukersNavn = "<Ukjent>",
                    orgAdresse = enhetTilAdresseFor(enhet),
                    orgNavn = enhet.navn,
                ))
                val vedtakDto = vedtak.toDto()

                // Svar ut spørringen
                call.respond(
                    Response(
                        resultat = if (vedtakDto.behandlingsresultat == Behandlingsresultat.INNVILGET) { Resultat.JA } else { Resultat.NEI },
                        sats = vedtakDto.sats,
                        satsBeløp = vedtakDto.beløp,
                        navReferanse = vedtakDto.id,
                    )
                )
            } catch (e: Exception) {
                log.error(e) { "Feil i krav oppretting" }
                call.respond(HttpStatusCode.InternalServerError, "Feil i krav oppretting")
            }
        }
    }
}
