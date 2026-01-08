package no.nav.syfo.client.behandlendeenhet

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val CALL_BEHANDLENDEENHET_BASE = "${METRICS_NS}_call_behandlendeenhet"
const val CALL_BEHANDLENDEENHET_SUCCESS = "${CALL_BEHANDLENDEENHET_BASE}_success_count"
const val CALL_BEHANDLENDEENHET_FAIL = "${CALL_BEHANDLENDEENHET_BASE}_fail_count"
const val CALL_BEHANDLENDEENHET_CACHE_HIT = "${CALL_BEHANDLENDEENHET_BASE}_cache_hit"
const val CALL_BEHANDLENDEENHET_CACHE_MISS = "${CALL_BEHANDLENDEENHET_BASE}_cache_miss"

val COUNT_CALL_BEHANDLENDEENHET_SUCCESS: Counter = Counter
    .builder(CALL_BEHANDLENDEENHET_SUCCESS)
    .description("Counts the number of successful calls to Syfobehandlendeenhet")
    .register(METRICS_REGISTRY)
val COUNT_CALL_BEHANDLENDEENHET_FAIL: Counter = Counter
    .builder(CALL_BEHANDLENDEENHET_FAIL)
    .description("Counts the number of failed calls to Syfobehandlendeenhet")
    .register(METRICS_REGISTRY)
val COUNT_CALL_BEHANDLENDEENHET_CACHE_HIT: Counter = Counter
    .builder(CALL_BEHANDLENDEENHET_CACHE_HIT)
    .description("Counts the number of cache hits for Syfobehandlendeenhet")
    .register(METRICS_REGISTRY)
val COUNT_CALL_BEHANDLENDEENHET_CACHE_MISS: Counter = Counter
    .builder(CALL_BEHANDLENDEENHET_CACHE_MISS)
    .description("Counts the number of cache miss for Syfobehandlendeenhet")
    .register(METRICS_REGISTRY)
