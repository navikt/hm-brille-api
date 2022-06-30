package no.nav.hjelpemidler.brille.redis

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import redis.clients.jedis.JedisPooled

private val log = KotlinLogging.logger {}

class RedisClient(val redisProps: Configuration.RedisProperties = Configuration.redisProperties) {
    val jedis = JedisPooled(redisProps.host, redisProps.port)

    fun erOptiker(fnr: String): Boolean? {
        val erOptiker = jedis.get("fnr:$fnr:hpr:er.optiker")?.toBoolean()
        log.info { "erOptiker($fnr) = $erOptiker" }
        return erOptiker
    }

    fun setErOptiker(fnr: String, erOptiker: Boolean) {
        log.info { "setErOptiker($fnr, $erOptiker)" }
        jedis.setex("fnr:$fnr:hpr:er.optiker", redisProps.hprExpirySeconds, erOptiker.toString())
    }
}
