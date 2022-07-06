package no.nav.hjelpemidler.brille

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.enhetsregisteret.Organisasjonsnummer
import no.nav.hjelpemidler.brille.enhetsregisteret.Postadresse
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapBarn
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapClient
import no.nav.hjelpemidler.brille.virksomhet.VirksomhetModell
import no.nav.hjelpemidler.brille.virksomhet.VirksomhetStore
import org.postgresql.util.PSQLException

private val log = KotlinLogging.logger { }

// FIXME: Remove eventually
@Deprecated("fjernes")
fun Route.testApi(
    medlemskapClient: MedlemskapClient,
    medlemskapBarn: MedlemskapBarn,
    virksomhetStore: VirksomhetStore,
    enhetsregisteretService: EnhetsregisteretService,
) {
    route("/test") {
        post("/medlemskap-client") {
            data class Request(val fnr: String)

            val fnr = call.receive<Request>().fnr
            call.respond(medlemskapClient.slåOppMedlemskap(fnr))
        }

        post("/medlemskap-barn") {
            data class Request(val fnr: String)

            val fnr = call.receive<Request>().fnr
            call.respond(medlemskapBarn.sjekkMedlemskapBarn(fnr))
        }

        get("/virksomhet/{orgnr}") {
            val organisasjonsnummer =
                call.parameters["orgnr"] ?: error("Mangler orgnr i url")

            val virksomhet = virksomhetStore.hentVirksomhet(organisasjonsnummer)
                ?: return@get call.respond(
                    status = HttpStatusCode.NotFound,
                    "Ingen virksomhet funnet for orgnr. $organisasjonsnummer"
                )

            val organisasjon = enhetsregisteretService.hentOrganisasjonsenhet(Organisasjonsnummer(organisasjonsnummer))
                ?: return@get call.respond(HttpStatusCode.NotFound, "Fant ikke orgenhet for orgnr $organisasjonsnummer")

            data class Response(
                val orgnr: String,
                val kontonr: String,
                val harNavAvtale: Boolean,
                val orgnavn: String,
                val forretningsadresse: Postadresse?,
                val erOptikerVirksomet: Boolean,
            )

            val response = Response(
                orgnr = organisasjon.organisasjonsnummer,
                kontonr = virksomhet.kontonr,
                harNavAvtale = virksomhet.harNavAvtale,
                orgnavn = organisasjon.navn,
                forretningsadresse = organisasjon.forretningsadresse,
                erOptikerVirksomet = listOf(
                    organisasjon.naeringskode1,
                    organisasjon.naeringskode2,
                    organisasjon.naeringskode3
                ).any { it?.kode == "47.782" },
            )

            call.respond(response)
        }

        post("/virksomhet") {
            val virksomhet = call.receive<VirksomhetModell>()

            try {
                virksomhetStore.lagreVirksomhet(virksomhet)
            } catch (e: PSQLException) {
                log.error(e) { "Lagring av virksomhet feilet" }
                if (e.message?.contains("duplicate key") == true &&
                    e.message?.contains("virksomhet_pkey") == true
                ) {
                    return@post call.response.status(HttpStatusCode.Conflict)
                }
                return@post call.response.status(HttpStatusCode.InternalServerError)
            }

            call.response.status(HttpStatusCode.Created)
        }
    }
}
