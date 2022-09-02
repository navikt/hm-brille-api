package no.nav.hjelpemidler.brille.internal

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.binder.MeterBinder
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry

fun Application.setupMetrics(extraMetrics: List<MeterBinder> = emptyList()) {
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
        meterBinders = listOf(
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            ProcessorMetrics(),
            JvmThreadMetrics(),
            LogbackMetrics(),
        ) + extraMetrics
    }

    routing {
        get("/internal/metrics") {
            call.respond(appMicrometerRegistry.scrape())
        }
    }
}
