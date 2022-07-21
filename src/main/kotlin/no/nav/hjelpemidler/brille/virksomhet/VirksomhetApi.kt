package no.nav.hjelpemidler.brille.virksomhet

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretClientException
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.enhetsregisteret.Organisasjonsenhet
import no.nav.hjelpemidler.brille.extractFnr
import no.nav.hjelpemidler.brille.vedtak.VedtakStore

private val log = KotlinLogging.logger {}

fun Route.virksomhetApi(
    vedtakStore: VedtakStore,
    enhetsregisteretService: EnhetsregisteretService,
    virksomhetStore: VirksomhetStore,
) {
    route("/virksomheter") {
        get {
            val fnrOptiker = call.extractFnr()

            val tidligereBrukteOrgnrForOptiker: List<String> =
                vedtakStore.hentTidligereBrukteOrgnrForInnsender(fnrOptiker)

            try {
                val organisasjoner: List<Organisasjon> = tidligereBrukteOrgnrForOptiker.map {
                    val enhet: Organisasjonsenhet =
                        enhetsregisteretService.hentOrganisasjonsenhet(it)
                            ?: return@get call.respond(
                                HttpStatusCode.NotFound,
                                "Fant ikke organisasjonsenhet for orgnr: $it"
                            )
                    Organisasjon(
                        orgnr = enhet.orgnr,
                        navn = enhet.navn,
                        adresse = adresseFor(enhet),
                        aktiv = true

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

        get("/{orgnr}") {
            val orgnr =
                call.parameters["orgnr"] ?: error("Mangler orgnr i url")

            val virksomhet = virksomhetStore.hentVirksomhetForOrganisasjon(orgnr)
            log.info { "Søker etter $orgnr fant $virksomhet"  }
            val harAktivNavAvtale = virksomhet?.aktiv ?: false



            val enhet = enhetsregisteretService.hentOrganisasjonsenhet(orgnr)
                ?: return@get call.respond(HttpStatusCode.NotFound, "Fant ikke organisasjonsenhet for orgnr: $orgnr")

            val response = Organisasjon(
                orgnr = enhet.orgnr,
                navn = enhet.navn,
                aktiv = harAktivNavAvtale,
                adresse = adresseFor(enhet),
            )

            call.respond(response)
        }
    }
}

private fun adresseFor(
    enhet: Organisasjonsenhet
) = if (enhet.forretningsadresse != null) {
    "${enhet.forretningsadresse.adresse.first()}, ${enhet.forretningsadresse.postnummer} ${enhet.forretningsadresse.poststed}"
} else if (enhet.beliggenhetsadresse != null) {
    "${enhet.beliggenhetsadresse.adresse.first()}, ${enhet.beliggenhetsadresse.postnummer} ${enhet.beliggenhetsadresse.poststed}"
} else {
    ""
}
