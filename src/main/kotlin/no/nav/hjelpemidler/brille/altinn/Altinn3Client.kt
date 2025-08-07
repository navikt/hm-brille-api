package no.nav.hjelpemidler.brille.altinn

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.headers
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.cache.createCache
import no.nav.hjelpemidler.cache.getAsync
import no.nav.hjelpemidler.http.correlationId
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.http.openid.TokenSetProvider
import no.nav.hjelpemidler.http.openid.openID
import kotlin.time.Duration.Companion.hours

private val log = KotlinLogging.logger {}

class Altinn3Client(tokenSetProvider: TokenSetProvider) {
    private val authedClient: HttpClient = createHttpClient {
        openID(tokenSetProvider)
        defaultRequest {
            headers {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                correlationId()
            }
            url(Configuration.ALTINN_URL)
        }
    }

    private val publicClient: HttpClient = createHttpClient {
        defaultRequest {
            headers {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                correlationId()
            }
            url(Configuration.ALTINN_URL)
        }
    }

    private val policySubjectsCache = createCache {
        expireAfterWrite = 1.hours
    }.buildAsync<String, List<PolicySubjects.Subject>>()

    private enum class Resource(val resourceKey: String) {
        Utbertalingsrapport("nav_barnebriller_utbetalingsrapport"),
        OpprettAvtale("nav_barnebriller_opprette-avtale"),
    }

    private class PolicySubjects {
        enum class Type(val text: String) {
            @JsonProperty("urn:altinn:rolecode")
            RoleCode("urn:altinn:rolecode"),

            @JsonProperty("urn:altinn:accesspackage")
            AccessPackage("urn:altinn:accesspackage"),
        }

        data class Data(
            val type: Type,
            val value: String,
            val urn: String,
        )

        data class Response(
            val data: List<Data>,
        )

        data class Subject(
            val type: Type,
            val value: String,
        )
    }

    private suspend fun getPolicySubjectsFor(resourceKey: String): List<PolicySubjects.Subject> {
        return policySubjectsCache.getAsync(resourceKey) {
            log.info { "Henter policy subjects for resourceKey=$resourceKey og legger de i cache" }
            val response = publicClient.get("/resourceregistry/api/v1/resource/$resourceKey/policy/subjects")
            val body: PolicySubjects.Response = response.body()
            body.data.map {
                PolicySubjects.Subject(type = it.type, value = it.value)
            }
        }
    }

    private class AuthorizedParties {
        data class Reqeust(
            val type: String = "urn:altinn:person:identifier-no",
            val value: String,
        )

        enum class Type(val type: String) {
            Person("Person"),
            Organization("Organization"),
        }

        data class Response(
            val partyUuid: String,
            val partyId: Long,
            val type: String,
            val personId: String?,
            val unitType: String?,
            val organizationNumber: String?,
            val name: String,
            val isDeleted: Boolean,
            val onlyHierarchyElementWithNoAccess: Boolean,
            val authorizedResources: List<String>,
            val authorizedRoles: List<String>,
            val subunits: List<Response>,
        )
    }

    private suspend fun authorizedParties(
        fnr: String,
        includeAltinn2: Boolean = true,
    ): List<AuthorizedParties.Response> {
        // Generer maskinporten token og hent data fra altinn3 apiet
        val response = authedClient.post(
            "/accessmanagement/api/v1/resourceowner/authorizedparties" + when (includeAltinn2) {
                true -> "?includeAltinn2=true"
                false -> ""
            },
        ) {
            setBody(AuthorizedParties.Reqeust(value = fnr))
        }
        return response.body<List<AuthorizedParties.Response>>()
    }

    suspend fun hentAvgivere(fnr: String, tjeneste: Avgiver.Tjeneste): List<Avgiver> {
        // Hent relevante policy subjects for tjenesten sin ressurs
        val resourceKey = when (tjeneste) {
            Avgiver.Tjeneste.OPPGJØRSAVTALE -> Resource.OpprettAvtale.resourceKey
            Avgiver.Tjeneste.UTBETALINGSRAPPORT -> Resource.Utbertalingsrapport.resourceKey
        }
        val policySubjects = getPolicySubjectsFor(resourceKey)
        return authorizedParties(fnr)
            // Bare se på organisasjoner
            .filter { runCatching { AuthorizedParties.Type.valueOf(it.type) }.getOrNull() == AuthorizedParties.Type.Organization }
            // Flat ut underenheter slik at vi matcher oppsettet fra Altinn2, og ta vare på parentOrgnr for underenheter
            .flatMap {
                // Pakk ut underenheter ala. gammel implementasjon
                val underenheter = it.subunits.map { innerIt -> Pair(innerIt, it.organizationNumber) }.toTypedArray()
                listOf(
                    Pair(it.copy(subunits = emptyList()), null),
                    *underenheter,
                )
            }
            // Filtrer ut slettede enheter
            .filter { !it.first.isDeleted }
            // Bare inkluder resultater hvor vi har en rolle eller (TODO:) tilgangspakke i policy subjects ressursen som matcher
            .filter {
                if (resourceKey in it.first.authorizedResources) {
                    log.info { "Altinn3 tilgang gitt pga. eksplisitt deligert rettighet (authorizedResources): $resourceKey" }
                    true
                } else {
                    it.first.authorizedRoles.find { it0 ->
                        policySubjects.find { it1 -> it1.type == PolicySubjects.Type.RoleCode && it1.value.lowercase() == it0.lowercase() } != null
                    } != null
                }
            }
            // Gjenbruk gammel type
            .map {
                Avgiver(
                    navn = it.first.name,
                    orgnr = it.first.organizationNumber!!,
                    parentOrgnr = it.second,
                )
            }
    }

    suspend fun hentRettigheter(fnr: String, orgnr: String): Set<Avgiver.Tjeneste> {
        return authorizedParties(fnr)
            // Bare se på gitt organisasjoner
            .filter { runCatching { AuthorizedParties.Type.valueOf(it.type) }.getOrNull() == AuthorizedParties.Type.Organization }
            // Flat ut underenheter slik at vi matcher oppsettet fra Altinn2
            .flatMap {
                // Pakk ut underenheter ala. gammel implementasjon
                listOf(
                    *it.subunits.map { innerIt -> innerIt }.toTypedArray(),
                    it.copy(subunits = emptyList()),
                )
            }
            // Filtrer ut slettede enheter
            .filter { !it.isDeleted }
            // Filtrer ut den enheten eller underenheten som matcher gitt orgnr
            .find { it.organizationNumber == orgnr }
            // Oversett roller og tilgangspakker til et sett av tjeneste-typen som inneholder de som man har tilgang til
            ?.let { enhet ->
                // TODO: Støtt tilgangspakker / access packages i fremtiden!
                val enhetRollerPersonHar = enhet.authorizedRoles.map { it.lowercase() }.toSet()
                val harTjenesteRolleEllerNull: suspend (Avgiver.Tjeneste) -> Avgiver.Tjeneste? =
                    { tj: Avgiver.Tjeneste ->
                        val resourceKey = when (tj) {
                            Avgiver.Tjeneste.OPPGJØRSAVTALE -> Resource.OpprettAvtale.resourceKey
                            Avgiver.Tjeneste.UTBETALINGSRAPPORT -> Resource.Utbertalingsrapport.resourceKey
                        }
                        if (resourceKey in enhet.authorizedResources) {
                            log.info { "Altinn3 rettighet gitt pga. eksplisitt deligert rettighet (authorizedResources): $resourceKey" }
                            tj
                        } else {
                            getPolicySubjectsFor(resourceKey)
                                .filter { it.type == PolicySubjects.Type.RoleCode }
                                .map { it.value.lowercase() }
                                .find { enhetRollerPersonHar.contains(it) }
                                ?.let { tj }
                        }
                    }
                setOf(
                    // Hent policy subject for tjenesten Oppgjørsavtale, og sjekk om rollene person har gir tilgang
                    // til tjenesten, og da i så fall inkluder denne tjenesten i resultatet
                    harTjenesteRolleEllerNull(Avgiver.Tjeneste.OPPGJØRSAVTALE),

                    // Hent policy subject for tjenesten Utbetalingsrapport, og sjekk om rollene person har gir tilgang
                    // til tjenesten, og da i så fall inkluder denne tjenesten i resultatet
                    harTjenesteRolleEllerNull(Avgiver.Tjeneste.UTBETALINGSRAPPORT),
                ).filterNotNull().toSet()
            } ?: emptySet()
    }

    suspend fun test(fnr: String) {
        log.info { "Requesting resources for $fnr" }
        val response =
            authedClient.post("/accessmanagement/api/v1/resourceowner/authorizedparties?includeAltinn2=true") {
                setBody(AuthorizedParties.Reqeust(value = fnr))
            }
        log.info { "Result: ${response.status}" }

        val result = response.body<List<AuthorizedParties.Response>>()
        log.info { "Debug result: $result" }

        val orgs = result.filter {
            // Bare se på organisasjoner
            runCatching { AuthorizedParties.Type.valueOf(it.type) }.getOrNull() == AuthorizedParties.Type.Organization
        }.flatMap {
            // Pakk ut underenheter ala. gammel implementasjon
            listOf(
                *it.subunits.map { innerIt -> Pair(innerIt, it.organizationNumber) }.toTypedArray(),
                Pair(it.copy(subunits = emptyList()), null),
            )
        }
        log.info { "Organisasjoner: $orgs" }

        val avgivere = orgs.map {
            Avgiver(
                navn = it.first.name,
                orgnr = it.first.organizationNumber!!,
                parentOrgnr = it.second,
            )
        }
        log.info { "Avgivere: $avgivere" }
    }
}
