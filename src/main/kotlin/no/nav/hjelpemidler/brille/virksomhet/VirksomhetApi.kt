package no.nav.hjelpemidler.brille.virksomhet

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretClientException
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.enhetsregisteret.Organisasjonsenhet
import no.nav.hjelpemidler.brille.enhetsregisteret.Postadresse
import no.nav.hjelpemidler.brille.extractFnr
import no.nav.hjelpemidler.brille.model.Organisasjon
import no.nav.hjelpemidler.brille.model.TidligereBrukteOrganisasjonerForOptiker
import no.nav.hjelpemidler.brille.vedtak.VedtakStore

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
                val enhet: Organisasjonsenhet =
                    enhetsregisteretService.hentOrganisasjonsenhet(it)
                        ?: return@get call.respond(
                            HttpStatusCode.NotFound,
                            "Fant ikke organisasjonsenhet for orgnr: $it"
                        )
                Organisasjon(
                    orgnr = enhet.orgnr,
                    navn = enhet.navn,
                    forretningsadresse = if (enhet.forretningsadresse != null) "${enhet.forretningsadresse.adresse.first()}, ${enhet.forretningsadresse.postnummer} ${enhet.forretningsadresse.poststed}" else null,
                    beliggenhetsadresse = if (enhet.beliggenhetsadresse != null) "${enhet.beliggenhetsadresse.adresse.first()}, ${enhet.beliggenhetsadresse.postnummer} ${enhet.beliggenhetsadresse.poststed}" else null,
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
        val orgnr =
            call.parameters["orgnr"] ?: error("Mangler orgnr i url")

        val virksomhet = virksomhetStore.hentVirksomhetForOrganisasjon(orgnr)
            ?: return@get call.respond(
                status = HttpStatusCode.NotFound,
                "Ingen virksomhet funnet for orgnr. $orgnr"
            )

        val organisasjon = enhetsregisteretService.hentOrganisasjonsenhet(orgnr)
            ?: return@get call.respond(HttpStatusCode.NotFound, "Fant ikke orgenhet for orgnr $orgnr")

        data class Response(
            val orgnr: String,
            val orgNavn: String,
            val kontonr: String,
            val harNavAvtale: Boolean,
            val forretningsadresse: Postadresse?,
            val erOptikerVirksomhet: Boolean,
        )

        val response = Response(
            orgnr = organisasjon.orgnr,
            orgNavn = organisasjon.navn,
            kontonr = virksomhet.kontonr,
            harNavAvtale = virksomhet.harNavAvtale,
            forretningsadresse = organisasjon.forretningsadresse,
            erOptikerVirksomhet = setOf(
                organisasjon.naeringskode1,
                organisasjon.naeringskode2,
                organisasjon.naeringskode3
            ).any { it?.kode == "47.782" },
        )

        call.respond(response)
    }
}
