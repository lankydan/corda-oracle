package com.lankydanblog.tutorial.flows

import co.paralleluniverse.fibers.Suspendable
import com.lankydanblog.tutorial.services.MessageRepository
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.transactions.SignedTransaction

@InitiatingFlow
@StartableByRPC
@StartableByService
class ReplyToMessagesFlow : FlowLogic<List<SignedTransaction>>() {

  @Suspendable
  override fun call(): List<SignedTransaction> {
    return serviceHub.cordaService(MessageRepository::class.java)
      .findAllNewNotBySender(ourIdentity).also { logger.info("found states: $it") }.states.map {
      subFlow(
        ReplyToMessageFlow(it)
      )
    }
  }
}