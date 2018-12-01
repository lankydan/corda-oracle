package com.lankydanblog.tutorial.flows

import co.paralleluniverse.fibers.Suspendable
import com.lankydanblog.tutorial.contracts.MessageContract
import com.lankydanblog.tutorial.contracts.MessageContract.Commands.*
import com.lankydanblog.tutorial.states.StockState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
@StartableByRPC
@StartableByService
class ReplyToMessageFlow(private val stock: StateAndRef<StockState>) :
  FlowLogic<SignedTransaction>() {

  @Suspendable
  override fun call(): SignedTransaction {
    val response = response(stock)
    val stx = collectSignature(verifyAndSign(transaction(response)), response)
    return subFlow(FinalityFlow(stx))
  }

  private fun response(stock: StateAndRef<StockState>): StockState {
    val state = stock.state.data
    return state.copy(
      contents = "Thanks for your stock: ${state.contents}",
      recipient = state.sender,
      sender = state.recipient
    )
  }

  @Suspendable
  private fun collectSignature(
    transaction: SignedTransaction,
    response: StockState
  ): SignedTransaction =
    subFlow(CollectSignaturesFlow(transaction, listOf(initiateFlow(response.recipient))))

  private fun verifyAndSign(transaction: TransactionBuilder): SignedTransaction {
    transaction.verify(serviceHub)
    return serviceHub.signInitialTransaction(transaction)
  }

  private fun transaction(response: StockState) =
    TransactionBuilder(notary()).apply {
      addInputState(stock)
      addOutputState(response, MessageContract.CONTRACT_ID)
      addCommand(Reply(), response.participants.map(Party::owningKey))
    }

  private fun notary() = stock.state.notary
}

@InitiatedBy(ReplyToMessageFlow::class)
class ReplyToMessageResponder(val session: FlowSession) : FlowLogic<Unit>() {

  @Suspendable
  override fun call() {
    subFlow(object : SignTransactionFlow(session) {
      override fun checkTransaction(stx: SignedTransaction) {}
    })
  }
}