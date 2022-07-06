package no.nav.hjelpemidler.brille.pdl

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import java.util.Locale

// Common

data class PdlError(
    val message: String,
    val locations: List<PdlErrorLocation> = emptyList(),
    val path: List<String>? = emptyList(),
    val extensions: PdlErrorExtension,
)

data class PdlErrorLocation(
    val line: Int?,
    val column: Int?,
)

data class PdlErrorExtension(
    val code: String?,
    val classification: String,
)

// HentPerson

data class PdlOppslag(val pdlPersonResponse: PdlPersonResponse, val saksgrunnlag: JsonNode)

data class PdlPersonResponse(
    val errors: List<PdlError> = emptyList(),
    val data: PdlHentPerson?,
)

data class PdlHentPerson(
    val hentPerson: PdlPerson?,
)

data class PdlPerson(
    val navn: List<PdlPersonNavn> = emptyList(),
    val adressebeskyttelse: List<Adressebeskyttelse>? = emptyList(),
    val bostedsadresse: List<Bostedsadresse> = emptyList(),
    val deltBosted: List<DeltBosted> = emptyList(),
    val foedsel: List<Foedsel> = emptyList(),
    val foreldreansvar: List<Foreldreansvar> = emptyList(),
    val forelderBarnRelasjon: List<ForelderBarnRelasjon> = emptyList(),
    val fullmakt: List<Fullmakt> = emptyList(),
    val vergemaalEllerFremtidsfullmakt: List<VergemaalEllerFremtidsfullmakt> = emptyList(),
)

data class PdlPersonNavn(
    val fornavn: String,
    val mellomnavn: String? = null,
    val etternavn: String,
)

data class Adressebeskyttelse(
    val gradering: Gradering,
)

data class Bostedsadresse(
    val vegadresse: Vegadresse?,
    val matrikkeladresse: Matrikkeladresse?
)

data class DeltBosted(
    val startdatoForKontrakt: LocalDate,
    val sluttdatoForKontrakt: LocalDate?,
    val vegadresse: Vegadresse?,
    val matrikkeladresse: Matrikkeladresse?,
)

data class Vegadresse(
    val matrikkelId: Long?,
    val bruksenhetsnummer: String?,
)

data class Matrikkeladresse(
    val matrikkelId: Long?,
    val bruksenhetsnummer: String?,
)

data class Foedsel(val foedselsaar: String?, val foedselsdato: String?)

data class Foreldreansvar(
    val ansvar: String?,
    val ansvarlig: String?,
    val folkeregistermetadata: Folkeregistermetadata?,
)

data class ForelderBarnRelasjon(
    val relatertPersonsIdent: String?,
    val relatertPersonsRolle: ForelderBarnRelasjonRolle,
    val minRolleForPerson: ForelderBarnRelasjonRolle?,
    val folkeregistermetadata: Folkeregistermetadata?,
)

enum class ForelderBarnRelasjonRolle {
    BARN,
    MOR,
    FAR,
    MEDMOR
}

data class Fullmakt(
    val motpartsPersonident: String,
    val motpartsRolle: MotpartsRolle,
    val omraader: List<String>,
    val gyldigFraOgMed: LocalDate,
    val gyldigTilOgMed: LocalDate,
)

enum class MotpartsRolle {
    FULLMAKTSGIVER,
    FULLMEKTIG,
}

data class RelatertBiPerson(
    val navn: Personnavn?,
    val foedselsdato: LocalDate?,
    val statsborgerskap: String?,
    val kjoenn: KjoennType?,
)

data class Personnavn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
)

enum class KjoennType {
    MANN, KVINNE, UKJENT
}

data class Folkeregistermetadata(
    val gyldighetstidspunkt: LocalDateTime?,
    val opphoerstidspunkt: LocalDateTime?,
)

data class VergemaalEllerFremtidsfullmakt(
    val type: String?,
    val embete: String?,
    val vergeEllerFullmektig: VergeEllerFullmektig,
    val folkeregistermetadata: Folkeregistermetadata?,
)

data class VergeEllerFullmektig(
    val navn: Personnavn?,
    val motpartsPersonident: String?,
    val omfang: String?,
    val omfangetErInnenPersonligOmraade: Boolean,
)

enum class Gradering {
    STRENGT_FORTROLIG_UTLAND,
    STRENGT_FORTROLIG,
    FORTROLIG,
    UGRADERT
}

enum class PdlFeiltype {
    IKKE_FUNNET,
    TEKNISK_FEIL,
}

fun PdlPersonResponse.harFeilmeldinger(): Boolean {
    return this.errors.isNotEmpty()
}

fun PdlPersonResponse.feilType(): PdlFeiltype {
    return if (this.errors.map { it.extensions.code }
        .contains("not_found")
    ) PdlFeiltype.IKKE_FUNNET else PdlFeiltype.TEKNISK_FEIL
}

fun PdlPersonResponse.feilmeldinger(): String {
    return this.errors.joinToString(",") { "${it.message}. Type ${it.extensions.classification}:${it.extensions.code}" }
}

fun PdlPersonResponse.harDiskresjonskode(): Boolean = if (this.data == null) {
    false
} else {
    this.data.isKode6Or7()
}

fun PdlHentPerson.navn(): Pair<String, String> {
    val nameList = this.hentPerson?.navn
    if (nameList.isNullOrEmpty()) {
        return Pair("", "")
    }
    nameList[0].let {
        val fornavn = if (it.mellomnavn.isNullOrBlank()) {
            it.fornavn
        } else {
            "${it.fornavn} ${it.mellomnavn}"
        }
        return Pair(fornavn, it.etternavn)
    }
}

data class Adresse(
    val adresse: String?,
    val postnummer: String?,
    val kommunenummer: String?,
)

fun PdlHentPerson.isKode6Or7(): Boolean {
    val adressebeskyttelse = this.hentPerson?.adressebeskyttelse
    return if (adressebeskyttelse.isNullOrEmpty()) {
        false
    } else {
        return adressebeskyttelse.any {
            it.isKode6() || it.isKode7()
        }
    }
}

fun PdlHentPerson.alder(): Int? {
    val foedsel = this.hentPerson?.foedsel
    if (foedsel.isNullOrEmpty())
        return null

    val foedselsdatoString = foedsel[0].foedselsdato
    val foedselsaarString = foedsel[0].foedselsaar
    val alder = when {
        !foedselsdatoString.isNullOrBlank() -> {
            val foedselsdato = LocalDate.parse(foedselsdatoString, ISO_LOCAL_DATE)
            beregnAlder(foedselsdato)
        }
        !foedselsaarString.isNullOrBlank() -> {
            val foedselsaar = foedselsaarString.toInt()
            val sisteDagIFoedselsaar =
                LocalDate.of(foedselsaar, 12, 31) // Må anta "worst case" at personen ikke har bursdag før 31. desember
            beregnAlder(sisteDagIFoedselsaar)
        }
        else -> null
    }
    return alder
}

fun PdlHentPerson.fodselsdato(): LocalDate? {
    val foedselsdato = this.hentPerson?.foedsel?.firstOrNull()?.foedselsdato ?: return null
    return LocalDate.parse(foedselsdato, ISO_LOCAL_DATE)
}

private fun beregnAlder(foedselsdato: LocalDate) = Period.between(foedselsdato, LocalDate.now()).years

private fun String.capitalizeWord(): String {
    return this.split(" ")
        .joinToString(" ") {
            it.lowercase(Locale.getDefault())
                .replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString() }
        }
}

fun PdlPersonResponse.toPersonDto(fnr: String): PersonDetaljerDto {
    println(this)
    val person = this.data ?: throw RuntimeException("PDL response mangler data")
    val (fornavn, etternavn) = person.navn()
    val alder = person.alder()
    return PersonDetaljerDto(
        fnr = fnr,
        fornavn = fornavn.capitalizeWord(),
        etternavn = etternavn.capitalizeWord(),
        alder = alder,
        fodselsdato = person.fodselsdato(),
    )
}

fun Adressebeskyttelse.isKode6(): Boolean {
    return this.gradering == Gradering.STRENGT_FORTROLIG || this.gradering == Gradering.STRENGT_FORTROLIG_UTLAND
}

fun Adressebeskyttelse.isKode7(): Boolean {
    return this.gradering == Gradering.FORTROLIG
}

// HentIdent
data class PdlIdentResponse(
    val errors: List<PdlError>?,
    val data: PdlHentIdent?,
)

data class PdlHentIdent(
    val hentIdenter: PdlIdenter?,
)

data class PdlIdenter(
    val identer: List<PdlIdent>,
)

data class PdlIdent(
    val ident: String,
    val gruppe: String?,
)
