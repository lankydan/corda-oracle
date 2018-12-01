package com.lankydanblog.tutorial.flows

import co.paralleluniverse.fibers.Suspendable
import com.lankydanblog.tutorial.contracts.MessageContract.Commands.Reply
import com.lankydanblog.tutorial.services.MessageRepository
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
@StartableByRPC
class DeleteAllMessagesFromPartyByTypeFlow(
  private val party: Party,
  private val type: String
) :
  FlowLogic<SignedTransaction>() {

  @Suspendable
  override fun call(): SignedTransaction {
    logger.info("Replying to all $type messages")
    val stx = collectSignature(verifyAndSign(transaction()))
    val tx = subFlow(FinalityFlow(stx))
    logger.info("Finished replying to $type messages")
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

  private fun transaction(): TransactionBuilder {
    val messages =
      serviceHub.cordaService(MessageRepository::class.java)
        .findAllNewBySenderAndType(party, type).states
    return TransactionBuilder(notary()).apply {
      messages.forEach {
        addInputState(it)
      }
      addCommand(Reply(), listOf(ourIdentity, party).map(Party::owningKey))
    }
  }

  private fun notary(): Party =
    serviceHub.networkMapCache.notaryIdentities.single { it.name.organisation == "Notary-1" }
}

@InitiatedBy(DeleteAllMessagesFromPartyByTypeFlow::class)
class DeleteAllMessagesFromPartyByTypeResponder(val session: FlowSession) :
  FlowLogic<Unit>() {

  @Suspendable
  override fun call() {
    subFlow(object : SignTransactionFlow(session) {
      override fun checkTransaction(stx: SignedTransaction) {}
    })
  }
}