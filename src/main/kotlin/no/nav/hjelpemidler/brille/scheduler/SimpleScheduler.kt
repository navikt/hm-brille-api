package no.nav.hjelpemidler.brille.scheduler

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.hjelpemidler.brille.internal.MetricsConfig
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit

private val log = KotlinLogging.logger {}

/**
 * A very simple implementation of a scheduler using coroutines and leaderElection
 * An action that failed with an exception and is not caught will also cancel the job.
 */
abstract class SimpleScheduler(
    private val leaderElection: LeaderElection,
    private val delay: Duration = 1.minutes,
    private val metricsConfig: MetricsConfig,
    private val onlyWorkHours: Boolean = false,
) {
    private val job: Job
    private val mySchedulerName: String = this.javaClass.simpleName

    init {
        log.info { "Starting scheduler: $mySchedulerName with a delay of $delay" }
        if (onlyWorkHours) log.info { "$mySchedulerName task will only be launched during working hours." }
        job = CoroutineScope(Dispatchers.Default).launch {
            runTask()
        }
    }

    private suspend fun runTask() = coroutineScope {
        while (true) {
            delay(delay)
            val leader = leaderElection.isLeader()
            if (leader && (!onlyWorkHours || LocalDateTime.now().isWorkingHours())) {
                log.info { "Running $mySchedulerName" }
                launch {
                    try {
                        val time = System.currentTimeMillis()
                        action()
                        val duration = (System.currentTimeMillis() - time).milliseconds
                        metricsConfig.registry.counter("scheduler_duration_seconds", "name", mySchedulerName)
                            .increment(duration.toDouble(DurationUnit.SECONDS))
                        if (duration > delay) {
                            log.warn { "$mySchedulerName spent ${duration}ms which is greater than delayTime: $delay" }
                        }
                    } catch (e: Exception) {
                        log.error(e) { "Scheduler $mySchedulerName has failed with an exception, the scheduler will be stopped" }
                        throw e
                    }
                }
            } else {
                log.info {
                    "NOT running $mySchedulerName: isLeader: $leader, onlyWorkHours: $onlyWorkHours, isWorkingHours: ${
                        LocalDateTime.now().isWorkingHours()
                    }"
                }
            }
        }
    }

    abstract suspend fun action()

    fun cancel() {
        log.info { "Cancelling job: $mySchedulerName" }
        job.cancel()
    }
}

fun LocalDateTime.isWorkingHours(): Boolean {
    return !isWeekend() && isBetweenEightToFive()
}

fun LocalDateTime.isBetweenEightToFive(): Boolean = toLocalTime() in LocalTime.of(8, 0)..LocalTime.of(17, 0)
fun LocalDateTime.isWeekend(): Boolean = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
