package com.lankydanblog.tutorial.oracle.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.lankydanblog.tutorial.oracle.data.Stock
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor
import okhttp3.OkHttpClient
import okhttp3.Request

@CordaService
class StockRetriever(private val serviceHub: AppServiceHub) :
  SingletonSerializeAsToken() {

  private val client = OkHttpClient()
  private val mapper = ObjectMapper()

  fun getCurrent(symbol: String): Stock {
    log.info("Sending request for $symbol")
    val response = client.newCall(request(symbol)).execute()
    return response.body()?.let {
      log.info("Retrieved response for $symbol")
      mapper.readValue(it.string(), Stock::class.java)
    } ?: throw IllegalArgumentException("Stock with symbol: $symbol does not exist")
  }

  private fun request(symbol: String) =
    Request.Builder().url("https://api.iextrading.com/1.0/stock/$symbol/quote").build()

  private companion object {
    val log = loggerFor<StockRetriever>()
  }
}