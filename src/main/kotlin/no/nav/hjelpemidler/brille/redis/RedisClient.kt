package no.nav.hjelpemidler.brille.redis

import no.nav.hjelpemidler.brille.Configuration
import redis.clients.jedis.JedisPooled

class RedisClient(val redisProps: Configuration.RedisProperties = Configuration.redisProperties) {
    val jedis = JedisPooled(redisProps.host, redisProps.port)

    fun erOptiker(fnr: String): Boolean? = jedis.get("fnr:$fnr:hpr:er.optiker")?.toBoolean()

    fun setErOptiker(fnr: String, erOptiker: Boolean): String = jedis.setex("fnr:$fnr:hpr:er.optiker", redisProps.hprExpirySeconds, erOptiker.toString())
}
