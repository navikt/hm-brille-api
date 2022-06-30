package no.nav.hjelpemidler.brille.redis

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import redis.clients.jedis.JedisPooled

private val log = KotlinLogging.logger {}

class RedisClient(val redisProps: Configuration.RedisProperties = Configuration.redisProperties) {
    val jedis = JedisPooled(
        GenericObjectPoolConfig(),
        redisProps.host,
        redisProps.port,
        2 * 1000, // 2 seconds
        redisProps.password
    )

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
