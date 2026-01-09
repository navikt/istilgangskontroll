package no.nav.syfo.client.azuread

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val CALL_AZURE_AD_BASE = "${METRICS_NS}_call_azure_ad"
const val CALL_AZURE_AD_CACHE_HIT = "${CALL_AZURE_AD_BASE}_cache_hit"
const val CALL_AZURE_AD_CACHE_MISS = "${CALL_AZURE_AD_BASE}_cache_miss"

val COUNT_AZURE_AD_CACHE_HIT: Counter = Counter
    .builder(CALL_AZURE_AD_CACHE_HIT)
    .description("Counts the number of successful cache hits for AzureAd tokens")
    .register(METRICS_REGISTRY)
val COUNT_AZURE_AD_CACHE_MISS: Counter = Counter
    .builder(CALL_AZURE_AD_CACHE_MISS)
    .description("Counts the number of cache miss for AzureAd tokens")
    .register(METRICS_REGISTRY)
