package no.nav.syfo.testhelper

import no.nav.syfo.cache.RedisEnvironment
import redis.embedded.RedisServer

fun testRedisServer(
    redisConfig: RedisEnvironment,
): RedisServer = RedisServer.builder()
    .port(redisConfig.port)
    .setting("requirepass ${redisConfig.secret}")
    .build()
