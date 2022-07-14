package no.nav.hjelpemidler.brille.pdl

import com.expediagroup.graphql.client.converter.ScalarConverter
import java.time.LocalDate
import java.time.LocalDateTime

class LongConverter : ScalarConverter<Long> {
    override fun toJson(value: Long): Any = value.toString()
    override fun toScalar(rawValue: Any): Long = rawValue.toString().toLong()
}

class DateConverter : ScalarConverter<LocalDate> {
    override fun toJson(value: LocalDate): Any = value.toString()
    override fun toScalar(rawValue: Any): LocalDate = LocalDate.parse(rawValue.toString())
}

class DateTimeConverter : ScalarConverter<LocalDateTime> {
    override fun toJson(value: LocalDateTime): Any = value.toString()
    override fun toScalar(rawValue: Any): LocalDateTime = LocalDateTime.parse(rawValue.toString())
}
