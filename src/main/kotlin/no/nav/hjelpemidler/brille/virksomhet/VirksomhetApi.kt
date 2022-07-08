package no.nav.hjelpemidler.brille.virksomhet

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretClientException
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.enhetsregisteret.Organisasjonsenhet
import no.nav.hjelpemidler.brille.enhetsregisteret.Organisasjonsnummer
import no.nav.hjelpemidler.brille.enhetsregisteret.Postadresse
import no.nav.hjelpemidler.brille.extractFnr
import no.nav.hjelpemidler.brille.model.Organisasjon
import no.nav.hjelpemidler.brille.model.TidligereBrukteOrganisasjonerForOptiker
import no.nav.hjelpemidler.brille.vedtak.VedtakStore
import org.postgresql.util.PSQLException

private val log = KotlinLogging.logger {}

fun Route.virksomhetApi(
    vedtakStore: VedtakStore,
    enhetsregisteretService: EnhetsregisteretService,
    virksomhetStore: VirksomhetStore,
) {
    get("/orgnr") {
        val fnrOptiker = call.extractFnr()

        val tidligereBrukteOrgnrForOptiker: List<String> =
            vedtakStore.hentTidligereBrukteOrgnrForOptiker(fnrOptiker)

        try {
            val organisasjoner: List<Organisasjon> = tidligereBrukteOrgnrForOptiker.map {
                val orgEnhet: Organisasjonsenhet =
                    enhetsregisteretService.hentOrganisasjonsenhet(Organisasjonsnummer(it))
                        ?: return@get call.respond(
                            HttpStatusCode.NotFound,
                            "Fant ikke orgenhet for orgnr $it"
                        )
                Organisasjon(
                    orgnummer = orgEnhet.organisasjonsnummer,
                    navn = orgEnhet.navn,
                    forretningsadresse = if (orgEnhet.forretningsadresse != null) "${orgEnhet.forretningsadresse.adresse}, ${orgEnhet.forretningsadresse.postnummer} ${orgEnhet.forretningsadresse.poststed}" else null,
                    beliggenhetsadresse = if (orgEnhet.beliggenhetsadresse != null) "${orgEnhet.beliggenhetsadresse.adresse}, ${orgEnhet.beliggenhetsadresse.postnummer} ${orgEnhet.beliggenhetsadresse.poststed}" else null,
                )
            }
            val response = TidligereBrukteOrganisasjonerForOptiker(
                sistBrukteOrganisasjon = organisasjoner.firstOrNull(),
                tidligereBrukteOrganisasjoner = organisasjoner
            )
            call.respond(response)
        } catch (e: EnhetsregisteretClientException) {
            call.respond(TidligereBrukteOrganisasjonerForOptiker(null, emptyList()))
        }
    }

    get("/virksomhet/{orgnr}") {
        val organisasjonsnummer =
            call.parameters["orgnr"] ?: error("Mangler orgnr i url")

        val virksomhet = when (Configuration.profile) {
            // TODO: fjern denne ekstra sjekken når vi har på plass en måte (UI) å faktisk lagre virksomheter til databasen på (se POST /virksomhet)
            Configuration.Profile.DEV -> Virksomhet(
                orgnr = organisasjonsnummer,
                kontonr = "12345678910",
                fnrInnsender = "15084300133",
                navnInnsender = "Sedat Kronjuvel",
                harNavAvtale = true,
                avtaleVersjon = "123abc"
            )
            else -> virksomhetStore.hentVirksomhet(organisasjonsnummer)
                ?: return@get call.respond(
                    status = HttpStatusCode.NotFound,
                    "Ingen virksomhet funnet for orgnr. $organisasjonsnummer"
                )
        }

        val organisasjon = enhetsregisteretService.hentOrganisasjonsenhet(Organisasjonsnummer(organisasjonsnummer))
            ?: return@get call.respond(HttpStatusCode.NotFound, "Fant ikke orgenhet for orgnr $organisasjonsnummer")

        data class Response(
            val organisasjonsnummer: String,
            val kontonr: String,
            val harNavAvtale: Boolean,
            val orgnavn: String,
            val forretningsadresse: Postadresse?,
            val erOptikerVirksomhet: Boolean,
        )

        val response = Response(
            organisasjonsnummer = organisasjon.organisasjonsnummer,
            kontonr = virksomhet.kontonr,
            harNavAvtale = virksomhet.harNavAvtale,
            orgnavn = organisasjon.navn,
            forretningsadresse = organisasjon.forretningsadresse,
            erOptikerVirksomhet = listOf(
                organisasjon.naeringskode1,
                organisasjon.naeringskode2,
                organisasjon.naeringskode3
            ).any { it?.kode == "47.782" },
        )

        call.respond(response)
    }

    post("/virksomhet") {
        val virksomhet = call.receive<Virksomhet>()

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
