package com.lankydanblog.tutorial.flows

import co.paralleluniverse.fibers.Suspendable
import com.lankydanblog.tutorial.common.flows.CollectOracleStockPriceSignatureFlow
import com.lankydanblog.tutorial.common.services.StockRetriever
import com.lankydanblog.tutorial.contracts.StockContract
import com.lankydanblog.tutorial.contracts.StockContract.Commands.GiveAway
import com.lankydanblog.tutorial.states.StockGiftState
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.util.function.Predicate

@InitiatingFlow
@StartableByRPC
class GiveAwayStockFlow(
  private val symbol: String,
  private val amount: Long,
  private val recipient: String
) :
  FlowLogic<SignedTransaction>() {

  @Suspendable
  override fun call(): SignedTransaction {
    val recipientParty = party()
    val oracle = oracle()
    val transaction =
      collectRecipientSignature(
        verifyAndSign(transaction(recipientParty, oracle)),
        recipientParty
      )
    val allSignedTransaction = collectOracleSignature(transaction, oracle)
    return subFlow(FinalityFlow(allSignedTransaction))
  }

  private fun party(): Party =
    serviceHub.networkMapCache.getPeerByLegalName(CordaX500Name.parse(recipient))
      ?: throw IllegalArgumentException("Party does not exist")

  private fun priceOfStock(): Double =
    serviceHub.cordaService(StockRetriever::class.java).getCurrent(symbol).price

  @Suspendable
  private fun collectRecipientSignature(
    transaction: SignedTransaction,
    party: Party
  ): SignedTransaction {
    val signature = subFlow(
      CollectSignatureFlow(
        transaction,
        initiateFlow(party),
        party.owningKey
      )
    ).single()
    return transaction.withAdditionalSignature(signature)
  }

  private fun verifyAndSign(transaction: TransactionBuilder): SignedTransaction {
    transaction.verify(serviceHub)
    return serviceHub.signInitialTransaction(transaction)
  }

  private fun transaction(recipientParty: Party, oracle: Party): TransactionBuilder =
    TransactionBuilder(notary()).apply {
      val priceOfStock = priceOfStock()
      addOutputState(state(recipientParty, priceOfStock), StockContract.CONTRACT_ID)
      addCommand(
        GiveAway(symbol, priceOfStock),
        listOf(recipientParty, oracle).map(Party::owningKey)
      )
    }

  private fun oracle(): Party = serviceHub.networkMapCache.getPeerByLegalName(
    CordaX500Name(
      "Oracle",
      "London",
      "GB"
    )
  )
    ?: throw IllegalArgumentException("Oracle does not exist")

  private fun state(party: Party, priceOfStock: Double): StockGiftState =
    StockGiftState(
      symbol = symbol,
      amount = amount,
      price = priceOfStock * amount,
      recipient = party
    )

  private fun notary(): Party = serviceHub.networkMapCache.notaryIdentities.first()

  private fun filteredTransaction(
    transaction: SignedTransaction,
    oracle: Party
  ): FilteredTransaction =
    transaction.buildFilteredTransaction(Predicate {
      when (it) {
        is Command<*> -> oracle.owningKey in it.signers && it.value is GiveAway
        else -> false
      }
    })

  @Suspendable
  private fun collectOracleSignature(
    transaction: SignedTransaction,
    oracle: Party
  ): SignedTransaction {
    val filtered = filteredTransaction(transaction, oracle)
    val signature = subFlow(CollectOracleStockPriceSignatureFlow(oracle, filtered))
    return transaction.withAdditionalSignature(signature)
  }
}

@InitiatedBy(GiveAwayStockFlow::class)
class SendMessageResponder(val session: FlowSession) : FlowLogic<Unit>() {

  @Suspendable
  override fun call() {
    subFlow(object : SignTransactionFlow(session) {
      override fun checkTransaction(stx: SignedTransaction) {}
    })
  }
}