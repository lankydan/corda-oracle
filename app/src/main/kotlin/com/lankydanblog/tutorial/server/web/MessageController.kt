package com.lankydanblog.tutorial.server.web

import com.lankydanblog.tutorial.flows.DeleteAllMessagesFromPartyByTypeFlow
import com.lankydanblog.tutorial.flows.DeleteAllMessagesFromPartyFlow
import com.lankydanblog.tutorial.flows.ReplyToMessagesFlow
import com.lankydanblog.tutorial.flows.SendMessageFlow
import com.lankydanblog.tutorial.server.NodeRPCConnection
import com.lankydanblog.tutorial.server.dto.Message
import com.lankydanblog.tutorial.states.StockState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI
import java.util.*

@RestController
@RequestMapping("/messages")
class MessageController(rpc: NodeRPCConnection) {

  private val proxy = rpc.proxy

  @PostMapping
  fun send(@RequestBody message: Message): ResponseEntity<StockState> =
    UUID.randomUUID().let {
      ResponseEntity.created(URI("/messages/$it")).body(
        proxy.startFlow(
          ::SendMessageFlow,
          state(message, it)
        ).returnValue.getOrThrow().coreTransaction.outputStates.first() as StockState
      )
    }

  private fun state(message: Message, id: UUID) =
    StockState(
      sender = proxy.nodeInfo().legalIdentities.first(),
      recipient = parse(message.recipient),
      contents = message.contents,
      type = message.type,
      linearId = UniqueIdentifier(id.toString())
    )

  private fun parse(party: String) =
    proxy.wellKnownPartyFromX500Name(CordaX500Name.parse(party))
      ?: throw IllegalArgumentException("Unknown party name.")

  @PostMapping("/replyAll")
  fun replyAll(): ResponseEntity<List<StockState>> =
    ResponseEntity.ok(
      proxy.startFlow(
        ::ReplyToMessagesFlow
      ).returnValue.getOrThrow().flatMap { it.coreTransaction.outputStates } as List<StockState>
    )

  @PostMapping("/deleteAllByParty")
  fun deleteAllByParty(@RequestParam party: String): ResponseEntity<List<StateRef>> =
    ResponseEntity.ok(
      proxy.startFlow(
        ::DeleteAllMessagesFromPartyFlow, parse(party)
      ).returnValue.getOrThrow().coreTransaction.inputs
    )

  @PostMapping("/deleteAllByPartyAndType")
  fun deleteAllByPartyAndType(
    @RequestParam party: String,
    @RequestParam type: String
  ): ResponseEntity<List<StateRef>> = ResponseEntity.ok(
    proxy.startFlow(
      ::DeleteAllMessagesFromPartyByTypeFlow, parse(party), type
    ).returnValue.getOrThrow().coreTransaction.inputs
  )
}