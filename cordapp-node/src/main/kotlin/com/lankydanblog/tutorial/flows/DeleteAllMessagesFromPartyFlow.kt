package com.lankydanblog.tutorial.flows

import co.paralleluniverse.fibers.Suspendable
import com.lankydanblog.tutorial.contracts.MessageContract.Commands.Reply
import com.lankydanblog.tutorial.services.MessageRepository
import com.lankydanblog.tutorial.states.StockState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
@StartableByRPC
class DeleteAllMessagesFromPartyFlow(private val party: Party) :
  FlowLogic<SignedTransaction>() {

  @Suspendable
  override fun call(): SignedTransaction {
    logger.info("Replying to all messages")
    val stx = collectSignature(verifyAndSign(transaction()))
    val tx = subFlow(FinalityFlow(stx))
    logger.info("Finished replying to messages")
    return tx
  }

  @Suspendable
  private fun collectSignature(
    transaction: SignedTransaction
  ): SignedTransaction =
    subFlow(CollectSignaturesFlow(transaction, listOf(initiateFlow(party))))

  private fun verifyAndSign(transaction: TransactionBuilder): SignedTransaction {
    transaction.verify(serviceHub)
    return serviceHub.signInitialTransaction(transaction)
  }

// @Suspendable
// private fun transaction(): TransactionBuilder {
//   val messages =
//     serviceHub.cordaService(MessageRepository::class.java)
//       .findAllNewBySender(party)
//       .states.also { logger.info("messages from vault: $it") }
//   val notary = notary()
//   return TransactionBuilder(notary).apply {
//     messages.forEach {
//       addInputState(notaryChange(it, notary))
//     }
//      addCommand(Reply(), listOf(ourIdentity, party).map(Party::owningKey))
//   }
// }

  @Suspendable
  private fun transaction(): TransactionBuilder {
    val messages =
      serviceHub.cordaService(MessageRepository::class.java)
        .findAllNewBySender(party)
        .states.also { logger.info("messages from vault: $it") }
    val notary = notary(messages)
    return TransactionBuilder(notary).apply {
      messages.forEach {
        addInputState(notaryChange(it, notary))
      }
      addCommand(
        Reply(),
        (messages.flatMap { it.state.data.participants }.toSet() + ourIdentity).map(Party::owningKey)
      )
    }
  }

  @Suspendable
  private fun notaryChange(
    stock: StateAndRef<StockState>,
    notary: Party
  ): StateAndRef<StockState> =
    if (stock.state.notary != notary) {
      subFlow(
        NotaryChangeFlow(
          stock,
          notary
        )
      ).also { logger.info("Changed from Notary: ${stock.state.notary} to: ${it.state.notary}") }
    } else {
      stock
    }

//  private fun notary(): Party =
//    serviceHub.networkMapCache.notaryIdentities.single { it.name.organisation == "Notary-1" }

  private fun notary(messages: List<StateAndRef<StockState>>): Party =
    messages.map { it.state.notary }
      .groupingBy { it }
      .eachCount()
      .maxBy { (_, size) -> size }?.key ?: throw IllegalStateException("No Notary found")
}

@InitiatedBy(DeleteAllMessagesFromPartyFlow::class)
class DeleteAllMessagesFromPartyResponder(val session: FlowSession) : FlowLogic<Unit>() {

  @Suspendable
  override fun call() {
    subFlow(object : SignTransactionFlow(session) {
      override fun checkTransaction(stx: SignedTransaction) {}
    })
  }
}