package com.lankydanblog.tutorial.server.web

import com.lankydanblog.tutorial.flows.GiveAwayStockFlow
import com.lankydanblog.tutorial.server.NodeRPCConnection
import com.lankydanblog.tutorial.server.dto.StockGift
import com.lankydanblog.tutorial.states.StockGiftState
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.*

@RestController
@RequestMapping("/stock/gift")
class GiftStockController(rpc: NodeRPCConnection) {

  private val proxy = rpc.proxy

  @PostMapping
  fun send(@RequestBody gift: StockGift): ResponseEntity<StockGiftState> =
    UUID.randomUUID().let {
      ResponseEntity.created(URI("/messages/$it")).body(
        proxy.startFlow(
          ::GiveAwayStockFlow,
          gift.symbol, gift.amount, gift.recipient
        ).returnValue.getOrThrow().coreTransaction.outputStates.first() as StockGiftState
      )
    }
}