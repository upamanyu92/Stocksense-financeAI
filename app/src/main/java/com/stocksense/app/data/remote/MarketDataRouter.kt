package com.stocksense.app.data.remote

class MarketDataRouter(
    private val providers: List<MarketDataProvider>
) {
    suspend fun fetch(request: MarketDataRequest): MarketDataPayload? {
        val matchingProviders = providers.filter { it.isConfigured && it.supports(request) }
        val providerChain = if (matchingProviders.isNotEmpty()) {
            matchingProviders
        } else {
            providers.filter { it.isConfigured }
        }

        for (provider in providerChain) {
            val payload = runCatching { provider.fetch(request) }.getOrNull()
            if (payload?.stock != null || payload?.history?.isNotEmpty() == true) {
                return payload
            }
        }
        return null
    }

    fun hasConfiguredProviders(): Boolean = providers.any { it.isConfigured }

    fun configuredProviderIds(): List<String> =
        providers.filter { it.isConfigured }.map { it.providerId }
}
