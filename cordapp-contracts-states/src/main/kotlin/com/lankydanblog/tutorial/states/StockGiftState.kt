package com.lankydanblog.tutorial.states

import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party

data class StockGiftState(
  val symbol: String,
  val amount: Long,
  val price: Double,
  val recipient: Party,
  override val linearId: UniqueIdentifier = UniqueIdentifier(),
  override val participants: List<Party> = listOf(recipient)
) : LinearState