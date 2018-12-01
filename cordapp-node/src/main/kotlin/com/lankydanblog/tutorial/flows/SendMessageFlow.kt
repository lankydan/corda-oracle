package com.lankydanblog.tutorial.flows

import co.paralleluniverse.fibers.Suspendable
import com.lankydanblog.tutorial.contracts.MessageContract
import com.lankydanblog.tutorial.contracts.MessageContract.Commands.Send
import com.lankydanblog.tutorial.states.StockState
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
@StartableByRPC
@StartableByService
class SendMessageFlow(private val stock: StockState) :
  FlowLogic<SignedTransaction>() {

  @Suspendable
  override fun call(): SignedTransaction {
    logger.info("Started sending stock ${stock.contents}")
    val stx = collectSignature(verifyAndSign(transaction()))
    val tx = subFlow(FinalityFlow(stx))
    logger.info("Finished sending stock ${stock.contents}")
    return tx
  }

  @Suspendable
  private fun collectSignature(
    transaction: SignedTransaction
  ): SignedTransaction =
    subFlow(CollectSignaturesFlow(transaction, listOf(initiateFlow(stock.recipient))))

  private fun verifyAndSign(transaction: TransactionBuilder): SignedTransaction {
    transaction.verify(serviceHub)
    return serviceHub.signInitialTransaction(transaction)
  }

  private fun transaction() =
    TransactionBuilder(notary()).apply {
      addOutputState(stock, MessageContract.CONTRACT_ID)
      addCommand(Send(), stock.participants.map(Party::owningKey))
    }

  private fun notary(): Party {
    val index = stock.type.hashCode() % serviceHub.networkMapCache.notaryIdentities.size.also { logger.info("value of index: $it") }
    return serviceHub.networkMapCache.notaryIdentities.single { it.name.organisation == "Notary-$index" }
      .also { logger.info("Message: $stock is being sent to Notary: $it") }
  }
}

@InitiatedBy(SendMessageFlow::class)
class SendMessageResponder(val session: FlowSession) : FlowLogic<Unit>() {

  @Suspendable
  override fun call() {
    subFlow(object : SignTransactionFlow(session) {
      override fun checkTransaction(stx: SignedTransaction) {}
    })
  }
}