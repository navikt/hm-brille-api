package no.nav.hjelpemidler.brille.syfohelsenettproxy

import java.time.LocalDateTime

data class Behandler(
    val godkjenninger: List<Godkjenning>,
    val fnr: String?,
    val hprNummer: Int?,
    val fornavn: String?,
    val mellomnavn: String?,
    val etternavn: String?,
) {
    fun navn(): String {
        val parts = listOfNotNull(fornavn, mellomnavn, etternavn)
        if (parts.isEmpty()) {
            return "<Ukjent>"
        }
        return parts.joinToString(separator = " ") { it }
    }
}

data class Godkjenning(
    val helsepersonellkategori: Kode? = null,
    val autorisasjon: Kode? = null,
    val tillegskompetanse: List<Tilleggskompetanse>? = null,
)

data class Kode(
    val aktiv: Boolean,
    val oid: Int,
    val verdi: String?,
)

data class Tilleggskompetanse(
    val avsluttetStatus: Kode?,
    val eTag: String?,
    val gyldig: Periode?,
    val id: Int?,
    val type: Kode?,
)

data class Periode(
    val fra: LocalDateTime?,
    val til: LocalDateTime?,
)
