package no.nav.hjelpemidler.brille.scheduler

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import no.nav.hjelpemidler.brille.StubEngine
import no.nav.hjelpemidler.brille.engineFactory
import no.nav.hjelpemidler.brille.jsonMapper
import org.slf4j.LoggerFactory
import java.net.InetAddress

class LeaderElection(electorPath: String) {

    private val hostname = InetAddress.getLocalHost().hostName
    private var leader = ""
    private val electorUri = "http://" + electorPath
    private val engine: HttpClientEngine = engineFactory { StubEngine.leaderElection() }

    private val client = HttpClient(engine) {
        expectSuccess = true
    }

    init {
        LOG.info("leader election initialized this hostname is $hostname")
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(LeaderElection::class.java)
    }

    suspend fun isLeader(): Boolean {
        return hostname == getLeader()
    }
    private suspend fun getLeader(): String {
        val response = client.get(electorUri)
        if (response.status == HttpStatusCode.OK) {
            val elector = jsonMapper.readValue(response.bodyAsText(), Elector::class.java)
            leader = elector.name
            LOG.debug("Running leader election getLeader is {} ", leader)
        }
        return leader
    }
}

data class Elector(val name: String)
