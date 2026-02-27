package no.nav.hjelpemidler.brille.tid

import java.time.LocalDate

/**
 * Null-verdi for dato.
 */
val MANGLENDE_DATO: LocalDate = LocalDate.MAX

fun LocalDate?.mangler(): Boolean = this == null || this == MANGLENDE_DATO

infix fun LocalDate?.alderPå(dato: LocalDate): Int? = this?.until(dato)?.years
