package com.lankydanblog.tutorial.flows

import com.lankydanblog.tutorial.states.StockState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test

class ReplyToMessagesFlowTest {

  private lateinit var mockNetwork: MockNetwork
  private lateinit var partyA: StartedMockNode
  private lateinit var partyB: StartedMockNode
  private lateinit var notaryNode0: MockNetworkNotarySpec
  private lateinit var notaryNode1: MockNetworkNotarySpec

  @Before
  fun setup() {
    notaryNode0 = MockNetworkNotarySpec(CordaX500Name("Notary-0", "London", "GB"))
    notaryNode1 = MockNetworkNotarySpec(CordaX500Name("Notary-1", "London", "GB"))
    mockNetwork = MockNetwork(
      listOf(
        "com.lankydanblog"
      ),
      notarySpecs = listOf(notaryNode0, notaryNode1)
    )
    partyA =
      mockNetwork.createNode(
        MockNodeParameters(
          legalName = CordaX500Name(
            "PartyA",
            "Berlin",
            "DE"
          )
        )
      )

    partyB =
      mockNetwork.createNode(
        MockNodeParameters(
          legalName = CordaX500Name(
            "PartyB",
            "Berlin",
            "DE"
          )
        )
      )
    mockNetwork.runNetwork()
  }

  @After
  fun tearDown() {
    mockNetwork.stopNodes()
  }

  @Test
  fun `Flow runs without errors`() {
    val future1 = partyA.startFlow(
      SendMessageFlow(
        StockState(
          contents = "hi",
          recipient = partyB.info.singleIdentity(),
          sender = partyA.info.singleIdentity(),
          type = "POST",
          linearId = UniqueIdentifier()
        )
      )
    )
    mockNetwork.runNetwork()
    println("done: ${future1.get()}")

    partyA.startFlow(
      SendMessageFlow(
        StockState(
          contents = "hi",
          recipient = partyB.info.singleIdentity(),
          sender = partyA.info.singleIdentity(),
          type = "mail",
          linearId = UniqueIdentifier()
        )
      )
    )
    mockNetwork.runNetwork()
    println("done: ${future1.get()}")

//    partyB.transaction {
//      val a = partyB.services.vaultService.queryBy<StockState>(
//        QueryCriteria.VaultCustomQueryCriteria(
//          builder {
//            MessageSchema.MessageEntity::sender.equal(
//              partyA.info.singleIdentity().name.toString()
//            )
//          })
//      )
//      println(a)
//    }

    val future2 = partyA.startFlow(
      SendMessageFlow(
        StockState(
          contents = "hey",
          recipient = partyB.info.singleIdentity(),
          sender = partyA.info.singleIdentity(),
          type = "post",
          linearId = UniqueIdentifier()
        )
      )
    )
    mockNetwork.runNetwork()
    println("done: ${future2.get()}")

    val future4 = partyB.startFlow(ReplyToMessagesFlow())
    mockNetwork.runNetwork()
    println("done: ${future4.get()}")

    val future3 = partyA.startFlow(
      DeleteAllMessagesFromPartyFlow(partyB.info.singleIdentity())
    )
    mockNetwork.runNetwork()
    println("done: ${future3.get()}")

  }
}