package no.nav.hjelpemidler.brille.rapportering

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.altinn.AltinnService
import no.nav.hjelpemidler.brille.extractFnr
import no.nav.hjelpemidler.brille.vedtak.Kravlinje
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

private val log = KotlinLogging.logger {}

fun Route.rapportApi(rapportService: RapportService, altinnService: AltinnService) {
    route("/kravlinjer") {
        get("/paged/{orgnr}") {
            val orgnr = call.orgnr()
            if (!altinnService.harTilgangTilUtbetalingsrapport(
                    call.extractFnr(),
                    orgnr,
                )
            ) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }
            val limit = call.request.queryParameters["limit"]?.toInt() ?: 10
            val page = call.request.queryParameters["page"]?.toInt() ?: 1

            val kravFilter = call.request.queryParameters["periode"]?.let { KravFilter.valueOf(it) }

            val fraDato = call.request.queryParameters["fraDato"]?.toLocalDate()
            val tilDato = call.request.queryParameters["tilDato"]?.toLocalDate()?.plusDays(1)

            val referanseFilter = call.request.queryParameters["referanseFilter"] ?: ""

            val kravlinjer = runCatching {
                rapportService.hentPagedKravlinjer(
                    orgNr = orgnr,
                    kravFilter = kravFilter,
                    fraDato = fraDato,
                    tilDato = tilDato,
                    referanseFilter = referanseFilter,
                    limit = limit,
                    offset = (page - 1) * limit,
                )
            }.getOrElse { e ->
                log.error(e) { "Feil med oppslag i rapporten" }
                throw e
            }

            val pagedKravlinjeListe = PagedKravlinjeliste(
                kravlinjer = kravlinjer,
                totalCount = kravlinjer.total,
                currentPage = page,
                pageSize = limit
            )

            call.respond(HttpStatusCode.OK, pagedKravlinjeListe)
        }

        get("/csv/{orgnr}") {
            val orgnr = call.orgnr()
            if (!altinnService.harTilgangTilUtbetalingsrapport(
                    call.extractFnr(),
                    orgnr,
                )
            ) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            val kravFilter = call.request.queryParameters["periode"]?.let { KravFilter.valueOf(it) }

            val fraDato = call.request.queryParameters["fraDato"]?.toLocalDate()
            val tilDato = call.request.queryParameters["tilDato"]?.toLocalDate()?.plusDays(1)

            val referanseFilter = call.request.queryParameters["referanseFilter"] ?: ""

            val kravlinjer = rapportService.hentKravlinjer(
                orgNr = orgnr,
                kravFilter = kravFilter,
                fraDato = fraDato,
                tilDato = tilDato,
                referanseFilter = referanseFilter,
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
                contentType = ContentType.Text.CSV,
                producer = producer(kravlinjer)
            )
        }
    }
}

fun producer(kravlinjer: List<Kravlinje>): suspend OutputStream.() -> Unit = {
    // Legg til en BOM-prefix i content (byte order mark) som indikerer til Microsoft Excel at filen er UTF-8
    write("\uFEFF".toByteArray())

    write(
        listOf(
            "Avstemmingsreferanse",
            "Deres referanse",
            "NAV sin referanse",
            "Kravbeløp",
            "Dato - krav sendt inn",
            "Brillens bestillingsdato",
            "Sendt til utbetaling",
            "Dato - sendt til utbetaling",
            "Kommentar",
        )
            .joinToString(";")
            .toByteArray()
    )
    write("\n".toByteArray())

    val formatterDatoTid = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    kravlinjer.forEach {
        val beløp = "${it.beløp}".replace(".", ",")
        write(
            listOf(
                it.batchId ?: "",
                it.bestillingsreferanse,
                it.id,
                beløp,
                it.opprettet.format(formatterDatoTid),
                it.bestillingsdato,
                if (it.utbetalingsdato == null) "Nei" else "Ja",
                it.utbetalingsdato ?: "",
                if (it.slettet != null) "Merk: kravet ble slettet av NAV etter utbetaling, etter en henvendelse fra virksomheten." else "",
            )
                .joinToString(";")
                .toByteArray()
        )
        write("\n".toByteArray())
    }
}

fun String.toLocalDate() =
    if (this.isBlank()) {
        null
    } else {
        LocalDate.parse(
            this,
            DateTimeFormatter.ofPattern("dd.MM.uuuu")
        )
    }

private fun ApplicationCall.orgnr(): String = requireNotNull(parameters["orgnr"]) {
    "Mangler orgnr i URL"
}

data class PagedKravlinjeliste(
    val kravlinjer: List<Kravlinje>,
    val totalCount: Int,
    val currentPage: Int,
    val pageSize: Int,
)

enum class KravFilter {
    ALLE,
    SISTE3MND,
    HITTILAR,
    EGENDEFINERT
}
