package no.nav.hjelpemidler.brille.scheduler

import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.StubEngine
import no.nav.hjelpemidler.brille.engineFactory
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import java.net.InetAddress

private val log = KotlinLogging.logger {}

class LeaderElection {
    private val hostname = InetAddress.getLocalHost().hostName
    private var leader = ""
    private val electorUri = Configuration.ELECTOR_GET_URL
    private val engine: HttpClientEngine = engineFactory { StubEngine.leaderElection() }

    private val client = HttpClient(engine) {
        expectSuccess = true
    }

    init {
        log.info { "leader election initialized this hostname is $hostname" }
    }

    suspend fun isLeader(): Boolean {
        return hostname == getLeader()
    }

    private suspend fun getLeader(): String {
        val response = client.get(electorUri)
        if (response.status == HttpStatusCode.OK) {
            val elector = jsonMapper.readValue<Elector>(response.bodyAsText())
            leader = elector.name
            log.debug { "Running leader election, leader: $leader" }
        }
        return leader
    }
}

data class Elector(val name: String)
