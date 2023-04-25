package no.nav.hjelpemidler.brille.admin

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.adminAuditLogging
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.extractEmail
import no.nav.hjelpemidler.brille.rapportering.KravFilter
import no.nav.hjelpemidler.brille.rapportering.RapportService
import no.nav.hjelpemidler.brille.rapportering.producer
import no.nav.hjelpemidler.brille.rapportering.toLocalDate
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingStatus
import no.nav.hjelpemidler.brille.vedtak.SlettVedtakConflictException
import no.nav.hjelpemidler.brille.vedtak.SlettVedtakInternalServerErrorException
import no.nav.hjelpemidler.brille.vedtak.SlettVedtakService
import no.nav.hjelpemidler.brille.vedtak.SlettetAvType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Date

private val log = KotlinLogging.logger {}

fun Route.adminApi(
    adminService: AdminService,
    slettVedtakService: SlettVedtakService,
    enhetsregisteretService: EnhetsregisteretService,
    rapportService: RapportService,
) {
    route("/admin") {
        post("/sok") {
            data class Request(
                val query: String,
            )

            val query = call.receive<Request>().query.trim()

            call.adminAuditLogging(
                "søk",
                mapOf(
                    "query" to query
                )
            )

            if (!Regex("[0-9-]+").matches(query)) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    """{"error": "Ugyldig format: bare tall og bindestrek er tillatt"}"""
                )
            }

            if (query.count() == 11) {
                // Fnr
                data class ResponseFnr(
                    val vedtak: List<VedtakListe>,
                    val avvisning: Avvisning?,
                )

                val krav = adminService.hentVedtakListe(query)
                val avvisning = adminService.hentAvvisning(query, krav.firstOrNull())
                call.respond(
                    HttpStatusCode.OK,
                    ResponseFnr(
                        krav,
                        avvisning?.copy(
                            orgNavn = avvisning.orgnr.let { orgnr ->
                                enhetsregisteretService.hentOrganisasjonsenhet(
                                    orgnr
                                )?.navn
                            } ?: "<Ukjent>",
                        )
                    )
                )
            } else if (Regex("[0-9]{9}-[0-9]{8}").matches(query)) {
                val utbetaling = adminService.hentUtbetalinger(query).isNotEmpty()
                if (!utbetaling) {
                    return@post call.respond(HttpStatusCode.NotFound, """{"error": "Fant ikke utbetaling"}""")
                }
                data class Response(
                    val utbetalingsreferanse: String,
                )
                call.respond(Response(query))
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
                    "vedtakId" to vedtakId.toString()
                ),
                vedtak.innsenderFnr,
            )

            data class Response(
                val vedtakId: Long,
                val orgnr: String,
                val orgNavn: String,
                val innsendersNavn: String,
                val barnsNavn: String,
                val bestillingsreferanse: String,
                val bestillingsdato: LocalDate,
                val beløp: BigDecimal,
                val opprettet: LocalDateTime,
                val utbetalt: LocalDateTime?,
                val utbetalingsstatus: UtbetalingStatus?,
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
                    innsendersNavn = vedtak.innsenderNavn,
                    barnsNavn = vedtak.barnsNavn,
                    bestillingsreferanse = vedtak.bestillingsreferanse,
                    bestillingsdato = vedtak.bestillingsdato,
                    beløp = vedtak.beløp,
                    opprettet = vedtak.opprettet,
                    utbetalt = vedtak.utbetalingsdato,
                    utbetalingsstatus = vedtak.utbetalingsstatus,
                    utbetalingsreferanse = vedtak.batchId,
                    slettet = vedtak.slettet,
                    slettetAv = vedtak.slettetAv,
                    slettetAvType = vedtak.slettetAvType
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
                    "vedtakId" to vedtakId.toString()
                ),
                vedtak.innsenderFnr,
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

        get("/utbetaling/{utbetalingsRef}") {
            val utbetalingsRef = call.parameters["utbetalingsRef"]!!
            val utbetalinger = adminService.hentUtbetalinger(utbetalingsRef)

            if (utbetalinger.isEmpty()) {
                return@get call.respond(HttpStatusCode.NotFound, """{"error": "Fant ikke utbetalingen"}""")
            }

            call.adminAuditLogging(
                "detaljer utbetaling",
                mapOf(
                    "utbetalingsRef" to utbetalingsRef,
                ),
            )

            data class ResponseUtbetaling(
                val vedtakId: Long,
                val bestillingsreferanse: String,
                val barnsNavn: String,
                val beløp: BigDecimal,
                val slettet: LocalDateTime?,
            )

            data class Response(
                val utbetalingsreferanse: String,
                val orgnr: String,
                val orgNavn: String,
                val totalBeløp: BigDecimal,
                val utbetalinger: List<ResponseUtbetaling>,
            )

            call.respond(
                Response(
                    utbetalingsreferanse = utbetalingsRef,
                    orgnr = utbetalinger.first().orgnr,
                    orgNavn = enhetsregisteretService.hentOrganisasjonsenhet(utbetalinger.first().orgnr)?.navn
                        ?: "<Ukjent>",
                    totalBeløp = utbetalinger.sumOf { it.beløp },
                    utbetalinger = utbetalinger.map {
                        ResponseUtbetaling(
                            vedtakId = it.vedtakId,
                            bestillingsreferanse = it.bestillingsreferanse,
                            barnsNavn = it.barnsNavn,
                            beløp = it.beløp,
                            slettet = it.slettet
                        )
                    }
                )
            )
        }

        get("/csv/{orgnr}") {
            val orgnr = call.parameters["orgnr"]!!

            val kravFilter = call.request.queryParameters["periode"]?.let { KravFilter.valueOf(it) }

            val fraDato = call.request.queryParameters["fraDato"]?.toLocalDate()
            val tilDato = call.request.queryParameters["tilDato"]?.toLocalDate()?.plusDays(1)

            call.adminAuditLogging(
                "hent rapport csv",
                mapOf(
                    "orgnr" to orgnr,
                    "kravFilter" to kravFilter?.toString(),
                    "fraDato" to fraDato?.toString(),
                    "tilDato" to tilDato?.toString()
                ),
            )

            val kravlinjer = rapportService.hentKravlinjer(
                orgNr = orgnr,
                kravFilter = kravFilter,
                fraDato = fraDato,
                tilDato = tilDato
            )

            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName,
                    "${Date().time}.csv"
                )
                    .toString()
            )

            call.respondOutputStream(
                status = HttpStatusCode.OK,
                contentType = ContentType.Text.CSV.withParameter("charset", "utf-8"),
                producer = producer(kravlinjer)
            )
        }
    }
}
