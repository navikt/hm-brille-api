package no.nav.hjelpemidler.brille.admin

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.extractEmail
import no.nav.hjelpemidler.brille.extractName
import no.nav.hjelpemidler.brille.extractUUID
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.vedtak.SlettVedtakConflictException
import no.nav.hjelpemidler.brille.vedtak.SlettVedtakInternalServerErrorException
import no.nav.hjelpemidler.brille.vedtak.SlettVedtakService
import no.nav.hjelpemidler.brille.vedtak.SlettetAvType
import java.math.BigDecimal
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

fun Route.adminApi(
    adminService: AdminService,
    slettVedtakService: SlettVedtakService,
    enhetsregisteretService: EnhetsregisteretService
) {
    authenticateAdminUser {
        route("/admin") {
            post("/sok") {
                data class Request(
                    val query: String,
                )

                val query = call.receive<Request>().query.trim()

                call.adminAuditLogging(
                    "søk",
                    mapOf(
                        "query" to query,
                    )
                )

                if (!Regex("\\d+").matches(query)) {
                    return@post call.respond(HttpStatusCode.BadRequest, """{"error": "Ugyldig format: bare tall"}""")
                }

                if (query.count() == 11) {
                    // Fnr
                    val krav = adminService.hentVedtakListe(query)
                    call.respond(HttpStatusCode.OK, krav)
                } else {
                    // vedtakId oppslag
                    val vedtak = adminService.hentVedtak(query.toLong())
                        ?: return@post call.respond(HttpStatusCode.NotFound, """{"error": "Fant ikke krav"}""")

                    call.respond(HttpStatusCode.OK, """{"vedtakId": "${vedtak.vedtakId}"}""")
                }
            }

            get("/detaljer/{vedtakId}") {
                val vedtakId = call.parameters["vedtakId"]!!.toLong()
                val vedtak = adminService.hentVedtak(vedtakId)
                    ?: return@get call.respond(HttpStatusCode.NotFound, """{"error": "Fant ikke krav"}""")

                call.adminAuditLogging(
                    "detaljer vedtak",
                    mapOf(
                        "vedtakId" to vedtakId.toString(),
                    )
                )

                data class Response(
                    val vedtakId: Long,
                    val orgnr: String,
                    val orgNavn: String,
                    val barnsNavn: String,
                    val bestillingsreferanse: String,
                    val belop: BigDecimal,
                    val opprettet: LocalDateTime,
                    val utbetalt: LocalDateTime?,
                    val utbetalingsreferanse: String?,
                    val slettet: LocalDateTime?,
                    val slettetAv: String?,
                    val slettetAvType: SlettetAvType?,
                )

                call.respond(
                    Response(
                        vedtakId = vedtak.vedtakId,
                        orgnr = vedtak.orgnr,
                        orgNavn = enhetsregisteretService.hentOrganisasjonsenhet(vedtak.orgnr)?.navn ?: "<Ukjent>",
                        barnsNavn = vedtak.barnsNavn,
                        bestillingsreferanse = vedtak.bestillingsreferanse,
                        belop = vedtak.belop,
                        opprettet = vedtak.opprettet,
                        utbetalt = vedtak.utbetalingsdato,
                        utbetalingsreferanse = vedtak.batchId,
                        slettet = vedtak.slettet,
                        slettetAv = vedtak.slettetAv,
                        slettetAvType = vedtak.slettetAvType,
                    )
                )
            }

            delete("/detaljer/{vedtakId}") {
                val email = call.extractEmail()

                val vedtakId = call.parameters["vedtakId"]!!.toLong()
                val vedtak = adminService.hentVedtak(vedtakId)
                    ?: return@delete call.respond(HttpStatusCode.NotFound, """{"error": "Fant ikke krav"}""")

                if (vedtak.slettet != null) {
                    return@delete call.respond(HttpStatusCode.BadRequest, """{"error": "Kravet er allerede slettet"}""")
                }

                call.adminAuditLogging(
                    "slett vedtak",
                    mapOf(
                        "vedtakId" to vedtakId.toString(),
                    )
                )

                try {
                    slettVedtakService.slettVedtak(vedtak.vedtakId, email, SlettetAvType.NAV_ADMIN)
                    call.respond(HttpStatusCode.OK, "{}")
                } catch (e: SlettVedtakConflictException) {
                    call.respond(HttpStatusCode.Conflict, e.message!!)
                } catch (e: SlettVedtakInternalServerErrorException) {
                    call.respond(HttpStatusCode.InternalServerError, e.message!!)
                }
            }
        }
    }
}

fun ApplicationCall.adminAuditLogging(tag: String, params: Map<String, String>) {
    val defaultParams = mapOf(
        "uri" to request.uri.toString(),
        "method" to request.httpMethod.value,
        "oid" to extractUUID(),
        "email" to extractEmail(),
        "name" to extractName(),
    )

    val allParams = defaultParams.toMutableMap()
    allParams.putAll(params)

    val logMessage = "Admin api audit: $tag: ${jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(allParams)}"
    sikkerlogg.info(logMessage)
}
