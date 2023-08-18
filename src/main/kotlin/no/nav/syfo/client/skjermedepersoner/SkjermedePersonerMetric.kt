package no.nav.syfo.client.skjermedepersoner

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val CALL_SKJERMEDE_PERSONER_BASE = "${METRICS_NS}_call_skjermede_personer"
const val CALL_SKJERMEDE_PERSONER_SUCCESS = "${CALL_SKJERMEDE_PERSONER_BASE}_success_count"
const val CALL_SKJERMEDE_PERSONER_FAIL = "${CALL_SKJERMEDE_PERSONER_BASE}_fail_count"

val COUNT_CALL_SKJERMEDE_PERSONER_SUCCESS: Counter = Counter
    .builder(CALL_SKJERMEDE_PERSONER_SUCCESS)
    .description("Counts the number of successful calls to SkjermedePersonerPip - skjerming av person")
    .register(METRICS_REGISTRY)
val COUNT_CALL_SKJERMEDE_PERSONER_FAIL: Counter = Counter
    .builder(CALL_SKJERMEDE_PERSONER_FAIL)
    .description("Counts the number of failed calls to SkjermedePersonerPip -  skjerming av person")
    .register(METRICS_REGISTRY)
