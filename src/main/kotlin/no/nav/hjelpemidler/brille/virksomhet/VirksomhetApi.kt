package no.nav.hjelpemidler.brille.virksomhet

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretClientException
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.enhetsregisteret.Organisasjonsenhet
import no.nav.hjelpemidler.brille.extractFnr

private val log = KotlinLogging.logger {}

fun Route.virksomhetApi(
    databaseContext: DatabaseContext,
    enhetsregisteretService: EnhetsregisteretService,
) {
    route("/virksomheter") {
        get {
            val fnrOptiker = call.extractFnr()

            val tidligereBrukteOrgnrForOptiker: List<String> =
                transaction(databaseContext) { ctx ->
                    ctx.vedtakStore.hentTidligereBrukteOrgnrForInnsender(fnrOptiker)
                }

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
                        adresse = enhetTilAdresseFor(enhet),
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

            val virksomhet =
                transaction(databaseContext) { ctx -> ctx.virksomhetStore.hentVirksomhetForOrganisasjon(orgnr) }
            val harAktivNavAvtale = virksomhet?.aktiv ?: false
            log.info("Søker etter $orgnr har aktiv NavAvtale: $harAktivNavAvtale")
            val enhet = enhetsregisteretService.hentOrganisasjonsenhet(orgnr)
                ?: return@get call.respond(HttpStatusCode.NotFound, "Fant ikke organisasjonsenhet for orgnr: $orgnr")

            val response = Organisasjon(
                orgnr = enhet.orgnr,
                navn = enhet.navn,
                aktiv = harAktivNavAvtale,
                adresse = enhetTilAdresseFor(enhet),
            )

            call.respond(response)
        }
    }
}

fun enhetTilAdresseFor(
    enhet: Organisasjonsenhet,
) = if (enhet.forretningsadresse != null) {
    "${enhet.forretningsadresse.adresse.firstOrNull() ?: ""}, ${enhet.forretningsadresse.postnummer} ${enhet.forretningsadresse.poststed}"
} else if (enhet.beliggenhetsadresse != null) {
    "${enhet.beliggenhetsadresse.adresse.firstOrNull() ?: ""}, ${enhet.beliggenhetsadresse.postnummer} ${enhet.beliggenhetsadresse.poststed}"
} else {
    ""
}
