package com.lankydanblog.tutorial.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.lankydanblog.tutorial.data.Stock
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor
import okhttp3.OkHttpClient
import okhttp3.Request

@CordaService
class StockRetriever(serviceHub: AppServiceHub) :
  SingletonSerializeAsToken() {

  private val client = OkHttpClient()
  private val mapper = ObjectMapper()

  fun getCurrent(symbol: String): Stock {
    log.info("Sending request for $symbol")
    val response = client.newCall(request(symbol)).execute()
    return response.body()?.let {
      log.info("Retrieved response for $symbol")
      val json = it.string()
      require(json != "Unknown symbol") { "Stock with symbol: $symbol does not exist" }
      val tree = mapper.readTree(json)
      Stock(
        symbol = symbol,
        name = tree["companyName"].asText(),
        primaryExchange = tree["primaryExchange"].asText(),
        price = tree["latestPrice"].asDouble()
      )
    } ?: throw IllegalArgumentException("No response")
  }

  private fun request(symbol: String) =
    Request.Builder().url("https://api.iextrading.com/1.0/stock/$symbol/quote").build()

  private companion object {
    val log = loggerFor<StockRetriever>()
  }
}