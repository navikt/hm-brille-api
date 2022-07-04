package no.nav.hjelpemidler.brille.pdl

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

data class PdlPersonResponse(
    val errors: List<PdlError> = emptyList(),
    val data: PdlHentPerson?,
)

data class PdlHentPerson(
    val hentPerson: PdlPerson?,
)

data class PdlPerson(
    val navn: List<PdlPersonNavn>,
    val adressebeskyttelse: List<Adressebeskyttelse>? = emptyList(),
    val bostedsadresse: List<Bostedsadresse> = emptyList(),
    val foedsel: List<Foedsel> = emptyList(),
    val forelderBarnRelasjon: List<ForelderBarnRelasjon> = emptyList(),
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

data class Bostedsadresse(val vegadresse: Vegadresse?, val matrikkeladresse: Matrikkeladresse?)

data class Vegadresse(
    val matrikkelId: Long?,
    val adressenavn: String?,
    val postnummer: String?,
    val husnummer: String? = null,
    val kommunenummer: String? = null,
    val husbokstav: String? = null,
    val tilleggsnavn: String? = null,
)

data class Matrikkeladresse(val matrikkelId: Long?, val postnummer: String?, val kommunenummer: String?)

data class Foedsel(val foedselsaar: String?, val foedselsdato: String?)

data class ForelderBarnRelasjon(
    val relatertPersonsIdent: String?,
    val relatertPersonsRolle: ForelderBarnRelasjonRolle,
    val minRolleForPerson: ForelderBarnRelasjonRolle?,
    val relatertPersonUtenFolkeregisteridentifikator: RelatertBiPerson?,
    val folkeregistermetadata: Folkeregistermetadata?,
)

enum class ForelderBarnRelasjonRolle {
    BARN,
    MOR,
    FAR,
    MEDMOR
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
    val ajourholdstidspunkt: LocalDateTime?,
    val gyldighetstidspunkt: LocalDateTime?,
    val opphoerstidspunkt: LocalDateTime?,
    val kilde: String?,
    val aarsak: String?,
    val sekvens: Int?,
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

fun PdlPersonResponse.kommunenummer(): String? {
    val bostedsadresse = this.data?.hentPerson?.bostedsadresse?.get(0)
    return bostedsadresse?.vegadresse?.kommunenummer ?: bostedsadresse?.matrikkeladresse?.kommunenummer
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

fun PdlHentPerson.adresse(): Adresse {
    val bostedsadresseList = this.hentPerson?.bostedsadresse

    if (bostedsadresseList.isNullOrEmpty()) {
        return Adresse(null, null, null)
    }

    val vegAdresse = bostedsadresseList[0].vegadresse
    val matrikkelAdresse = bostedsadresseList[0].matrikkeladresse
    val adresse = vegAdresse?.let {
        listOfNotNull(
            it.adressenavn,
            it.husnummer,
            it.husbokstav
        ).joinToString(separator = " ")
    }.orEmpty()

    val postnummer = vegAdresse?.postnummer ?: matrikkelAdresse?.postnummer ?: ""
    val kommunenummer = vegAdresse?.kommunenummer ?: matrikkelAdresse?.kommunenummer ?: ""

    return Adresse(adresse, postnummer, kommunenummer)
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

private fun beregnAlder(foedselsdato: LocalDate) = Period.between(foedselsdato, LocalDate.now()).years

private fun String.capitalizeWord(): String {
    return this.split(" ")
        .joinToString(" ") {
            it.lowercase(Locale.getDefault())
                .replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString() }
        }
}

fun PdlPersonResponse.toPersonDto(fnr: String, hentPoststed: (String) -> String?): PersonDetaljerDto {
    println(this)
    val person = this.data ?: throw RuntimeException("PDL response mangler data")
    val (fornavn, etternavn) = person.navn()
    val (adresse, postnummer, kommunenummer) = person.adresse()
    val poststed = if (postnummer != null) hentPoststed(postnummer) else ""
    val alder = person.alder()
    return PersonDetaljerDto(
        fnr = fnr,
        fornavn = fornavn.capitalizeWord(),
        etternavn = etternavn.capitalizeWord(),
        adresse = adresse?.capitalizeWord(),
        postnummer = postnummer,
        poststed = poststed?.capitalizeWord(),
        alder = alder,
        kommunenummer = kommunenummer
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
