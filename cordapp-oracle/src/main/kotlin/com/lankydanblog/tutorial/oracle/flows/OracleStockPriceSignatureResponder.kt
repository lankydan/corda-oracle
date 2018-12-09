package com.lankydanblog.tutorial.oracle.flows

import co.paralleluniverse.fibers.Suspendable
import com.lankydanblog.tutorial.common.flows.CollectOracleStockPriceSignatureFlow
import com.lankydanblog.tutorial.contracts.StockContract.Commands.GiveAway
import com.lankydanblog.tutorial.oracle.services.StockPriceValidator
import net.corda.core.contracts.Command
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.unwrap
import java.security.PublicKey

@InitiatedBy(CollectOracleStockPriceSignatureFlow::class)
class OracleStockPriceSignatureResponder(private val session: FlowSession) : FlowLogic<Unit>() {

  @Suspendable
  override fun call() {
    val transaction = session.receive<FilteredTransaction>().unwrap { it }

    val key = key()

    val isValid = transaction.checkWithFun { element: Any ->
      when {
        element is Command<*> && element.value is GiveAway -> {
          val command = element.value as GiveAway
          (key in element.signers).also {
            validateStockPrice(
              command.symbol,
              command.price
            )
          }
        }
        else -> {
          log.info("Transaction: ${transaction.id} is invalid")
          false
        }
      }
    }

    if (isValid) {
      log.info("Transaction: ${transaction.id} is valid, signing with oracle key")
      session.send(serviceHub.createSignature(transaction, key))
    } else {
      throw InvalidStockPriceFlowException("Transaction: ${transaction.id} is invalid")
    }
  }

  private fun key(): PublicKey = serviceHub.myInfo.legalIdentities.first().owningKey

  private fun validateStockPrice(symbol: String, price: Double) = try {
    serviceHub.cordaService(StockPriceValidator::class.java).validate(symbol, price)
  } catch (e: IllegalArgumentException) {
    throw InvalidStockPriceFlowException(e.message)
  }

  private companion object {
    val log = loggerFor<OracleStockPriceSignatureResponder>()
  }
}

class InvalidStockPriceFlowException(message: String?) : FlowException(message)