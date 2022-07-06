package no.nav.hjelpemidler.brille.virksomhet

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretClientException
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.enhetsregisteret.Organisasjonsenhet
import no.nav.hjelpemidler.brille.enhetsregisteret.Organisasjonsnummer
import no.nav.hjelpemidler.brille.extractFnr
import no.nav.hjelpemidler.brille.model.Organisasjon
import no.nav.hjelpemidler.brille.model.TidligereBrukteOrganisasjonerForOptiker
import no.nav.hjelpemidler.brille.vedtak.VedtakStore

fun Route.virksomhetApi(vedtakStore: VedtakStore, enhetsregisteretService: EnhetsregisteretService) {
    get("/orgnr") {
        val fnrOptiker = call.extractFnr()

        val tidligereBrukteOrgnrForOptikker: List<String> =
            vedtakStore.hentTidligereBrukteOrgnrForOptiker(fnrOptiker)

        try {
            val organisasjoner: List<Organisasjon> = tidligereBrukteOrgnrForOptikker.map {
                val orgEnhet: Organisasjonsenhet =
                    enhetsregisteretService.hentOrganisasjonsenhet(Organisasjonsnummer(it))
                        ?: return@get call.respond(
                            HttpStatusCode.NotFound,
                            "Fant ikke orgenhet for orgnr $it"
                        )
                Organisasjon(
                    orgnummer = orgEnhet.organisasjonsnummer,
                    navn = orgEnhet.navn,
                    adresse = "${orgEnhet.forretningsadresse}, ${orgEnhet.forretningsadresse?.postnummer} ${orgEnhet.forretningsadresse?.poststed}"
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

    get("/enhetsregisteret/enheter/{organisasjonsnummer}") {
        val organisasjonsnummer =
            call.parameters["organisasjonsnummer"] ?: error("Mangler organisasjonsnummer i url")
        val organisasjonsenhet =
            enhetsregisteretService.hentOrganisasjonsenhet(Organisasjonsnummer(organisasjonsnummer))
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    "Fant ikke orgenhet for orgnr $organisasjonsnummer"
                )
        call.respond(organisasjonsenhet)
    }
}
