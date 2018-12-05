package com.lankydanblog.tutorial.contracts

import com.lankydanblog.tutorial.states.StockGiftState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

class StockContract : Contract {
  companion object {
    val CONTRACT_ID = StockContract::class.qualifiedName!!
  }

  interface Commands : CommandData {
    data class GiveAway(val symbol: String, val price: Double) : Commands
  }

  override fun verify(tx: LedgerTransaction) {
    val command = tx.commands.requireSingleCommand<Commands>().value
    when (command) {
      is Commands.GiveAway -> requireThat {
        val gift = tx.outputStates.single() as StockGiftState
        "Price of gift is equal to the amount multiplied by the price per stock" using (gift.price == command.price * gift.amount)
      }
    }
  }
}