package com.lankydanblog.tutorial.oracle.services

import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor

@CordaService
class StockPriceValidator(private val serviceHub: AppServiceHub) :
  SingletonSerializeAsToken() {

  fun validate(symbol: String, price: Double): Boolean =
    try {
      serviceHub.cordaService(StockRetriever::class.java).getCurrent(symbol).let {
        log.info("Expected price for $symbol - ${it.name}: $price Actual price: ${it.price}")
//      require(price == it.price) { "The price of $symbol is ${it.price}, not $price" }
        price == it.price
      }
    } catch (e: IllegalArgumentException) {
      log.info(e.message)
      false
    }

  private companion object {
    val log = loggerFor<StockPriceValidator>()
  }
}