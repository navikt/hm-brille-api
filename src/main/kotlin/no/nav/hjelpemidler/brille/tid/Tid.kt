package no.nav.hjelpemidler.brille.tid

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.time.Duration

private val timeZone: TimeZone = TimeZone.getTimeZone("Europe/Oslo")
private val zoneId = timeZone.toZoneId()

/**
 * Null-verdi for dato.
 */
val MANGLENDE_DATO: LocalDate = LocalDate.MAX

fun LocalDate?.mangler(): Boolean = this == null || this === MANGLENDE_DATO

infix fun LocalDate?.alderPå(dato: LocalDate): Int? = this?.until(dato)?.years

fun LocalDate.toInstant(): Instant =
    atStartOfDay(zoneId).toInstant()

fun LocalDateTime.toInstant(): Instant =
    atZone(timeZone.toZoneId()).toInstant()

fun Instant.toLocalDate(): LocalDate =
    ZonedDateTime.ofInstant(this, zoneId).toLocalDate()

fun Instant.toLocalDateTime(): LocalDateTime =
    ZonedDateTime.ofInstant(this, zoneId).toLocalDateTime()

operator fun Instant.minus(duration: Duration): Instant =
    minusNanos(duration.inWholeNanoseconds)

operator fun Instant.plus(duration: Duration): Instant =
    plusNanos(duration.inWholeNanoseconds)
