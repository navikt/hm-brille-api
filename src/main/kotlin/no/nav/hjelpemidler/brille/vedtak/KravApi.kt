package no.nav.hjelpemidler.brille.vedtak

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.hjelpemidler.brille.audit.AuditService
import no.nav.hjelpemidler.brille.extractFnr
import no.nav.hjelpemidler.brille.sats.SatsType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

fun Route.kravApi(vedtakService: VedtakService, auditService: AuditService) {
    post("/krav") {
        val kravDto = call.receive<KravDto>()
        val fnrInnsender = call.extractFnr()

        auditService.lagreOppslag(
            fnrInnlogget = fnrInnsender,
            fnrOppslag = kravDto.vilkårsgrunnlag.fnrBarn,
            oppslagBeskrivelse = "[POST] /krav - Innsending av krav"
        )

        val vedtak = vedtakService.lagVedtak(fnrInnsender, kravDto)

        call.respond(
            HttpStatusCode.OK,
            VedtakDto(
                id = vedtak.id,
                orgnr = vedtak.orgnr,
                bestillingsdato = vedtak.bestillingsdato,
                brillepris = vedtak.brillepris,
                bestillingsreferanse = vedtak.bestillingsreferanse,
                behandlingsresultat = vedtak.behandlingsresultat,
                sats = vedtak.sats,
                satsBeløp = vedtak.satsBeløp,
                satsBeskrivelse = vedtak.satsBeskrivelse,
                beløp = vedtak.beløp,
                opprettet = vedtak.opprettet,
            )
        )
    }
}

data class VedtakDto(
    val id: Long,
    val orgnr: String,
    val bestillingsdato: LocalDate,
    val brillepris: BigDecimal,
    val bestillingsreferanse: String,
    val behandlingsresultat: Behandlingsresultat,
    val sats: SatsType,
    val satsBeløp: Int,
    val satsBeskrivelse: String,
    val beløp: BigDecimal,
    val opprettet: LocalDateTime,
)
