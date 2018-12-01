package com.lankydanblog.tutorial.oracle.flows

import co.paralleluniverse.fibers.Suspendable
import com.lankydanblog.tutorial.contracts.MessageContract
import com.lankydanblog.tutorial.oracle.services.StockPriceValidator
import net.corda.core.contracts.Command
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.utilities.unwrap
import java.security.PublicKey

@InitiatedBy(SendMessageFlow::class)
class StockPriceOracleFlow(val session: FlowSession) : FlowLogic<Unit>() {

  @Suspendable
  override fun call() {
    val transaction = session.receive<FilteredTransaction>().unwrap { it }

    val key = key()

    val isValid = transaction.checkWithFun { element: Any ->
      when {
        element is Command<*> && element.value is MessageContract.Commands.Send -> {
          val cmdData = element.value as MessageContract.Commands.Send
          key in element.signers && validateStockPrice(cmdData.symbol, cmdData.price)
        }
        else -> false
      }
    }

    if(isValid) {
      serviceHub.createSignature(transaction, key)
    } else {
      throw InvalidStockPriceFlowException()
    }
  }

  private fun key(): PublicKey = serviceHub.myInfo.legalIdentities.first().owningKey

  private fun validateStockPrice(symbol: String, price: Double): Boolean =
    serviceHub.cordaService(StockPriceValidator::class.java).validate(symbol, price)
}

class InvalidStockPriceFlowException(cause: Throwable?) : FlowException(cause)