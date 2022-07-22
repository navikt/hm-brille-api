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
import no.nav.hjelpemidler.brille.altinn.AltinnService
import no.nav.hjelpemidler.brille.extractFnr
import no.nav.hjelpemidler.brille.vedtak.Kravlinje
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

fun Route.rapportApi(rapportService: RapportService, altinnService: AltinnService) {
    route("/kravlinjer") {
        get("/paged/{orgnr}") {
            val orgnr = call.orgnr()
            if (!altinnService.erHovedadministratorFor(call.extractFnr(), orgnr)) {
                call.respond(HttpStatusCode.Unauthorized)
            }
            val limit = call.request.queryParameters["limit"]?.toInt() ?: 20
            val page = call.request.queryParameters["page"]?.toInt() ?: 1

            val kravFilter = call.request.queryParameters["periode"]?.let { KravFilter.valueOf(it) }

            val fraDato = call.request.queryParameters["fraDato"]?.toLocalDate()
            val tilDato = call.request.queryParameters["tilDato"]?.toLocalDate()?.plusDays(1)

            val kravlinjer = rapportService.hentPagedKravlinjer(
                orgNr = orgnr,
                kravFilter = kravFilter,
                fraDato = fraDato,
                tilDato = tilDato,
                limit = limit,
                offset = (page - 1) * limit,
            )

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
            if (!altinnService.erHovedadministratorFor(call.extractFnr(), orgnr)) {
                call.respond(HttpStatusCode.Unauthorized)
            }

            val kravFilter = call.request.queryParameters["periode"]?.let { KravFilter.valueOf(it) }

            val fraDato = call.request.queryParameters["fraDato"]?.toLocalDate()
            val tilDato = call.request.queryParameters["tilDato"]?.toLocalDate()?.plusDays(1)

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
                contentType = ContentType.Text.CSV,
                producer = producer(kravlinjer)
            )
        }
    }
}

fun producer(kravlinjer: List<Kravlinje>): suspend OutputStream.() -> Unit = {
    write("NAV referanse, Deres referanse, Kravbeløp, Opprettet dato, Utbetalt".toByteArray())
    write("\n".toByteArray())
    kravlinjer.forEach {
        write("${it.id}, ${it.bestillingsreferanse}, ${it.beløp}, ${it.bestillingsdato}, Nei".toByteArray())
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
    val pageSize: Int
)

enum class KravFilter {
    ALLE,
    SISTE3MND,
    HITTILAR,
    EGENDEFINERT
}
