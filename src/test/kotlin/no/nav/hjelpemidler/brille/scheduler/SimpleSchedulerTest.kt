package no.nav.hjelpemidler.brille.scheduler

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SimpleSchedulerTest {
    @Test
    fun isWorkingHoursTest() {
        val weekend = LocalDateTime.of(2022, 8, 27, 13, 0, 0)
        weekend.isWeekend() shouldBe true
        weekend.isBetweenEightToFive() shouldBe true
        weekend.isWorkingHours() shouldBe false
    }
}
