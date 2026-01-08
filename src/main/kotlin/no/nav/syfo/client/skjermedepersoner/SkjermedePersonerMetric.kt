package no.nav.syfo.client.skjermedepersoner

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val CALL_SKJERMEDE_PERSONER_BASE = "${METRICS_NS}_call_skjermede_personer"
const val CALL_SKJERMEDE_PERSONER_SUCCESS = "${CALL_SKJERMEDE_PERSONER_BASE}_success_count"
const val CALL_SKJERMEDE_PERSONER_FAIL = "${CALL_SKJERMEDE_PERSONER_BASE}_fail_count"
const val CALL_SKJERMEDE_PERSONER_CACHE_HIT = "${CALL_SKJERMEDE_PERSONER_BASE}_cache_hit"
const val CALL_SKJERMEDE_PERSONER_CACHE_MISS = "${CALL_SKJERMEDE_PERSONER_BASE}_cache_miss"

val COUNT_CALL_SKJERMEDE_PERSONER_SUCCESS: Counter = Counter
    .builder(CALL_SKJERMEDE_PERSONER_SUCCESS)
    .description("Counts the number of successful calls to SkjermedePersonerPip - skjerming av person")
    .register(METRICS_REGISTRY)
val COUNT_CALL_SKJERMEDE_PERSONER_FAIL: Counter = Counter
    .builder(CALL_SKJERMEDE_PERSONER_FAIL)
    .description("Counts the number of failed calls to SkjermedePersonerPip -  skjerming av person")
    .register(METRICS_REGISTRY)
val COUNT_CALL_SKJERMEDE_PERSONER_CACHE_HIT: Counter = Counter
    .builder(CALL_SKJERMEDE_PERSONER_CACHE_HIT)
    .description("Counts the number of cache hits for SkjermedePersonerPip - skjerming av person")
    .register(METRICS_REGISTRY)
val COUNT_CALL_SKJERMEDE_PERSONER_CACHE_MISS: Counter = Counter
    .builder(CALL_SKJERMEDE_PERSONER_CACHE_MISS)
    .description("Counts the number of cache miss for SkjermedePersonerPip - skjerming av person")
    .register(METRICS_REGISTRY)
