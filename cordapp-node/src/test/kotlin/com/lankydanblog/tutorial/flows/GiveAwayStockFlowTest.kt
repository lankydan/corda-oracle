package com.lankydanblog.tutorial.flows

import net.corda.core.identity.CordaX500Name
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test

class GiveAwayStockFlowTest {

  private lateinit var mockNetwork: MockNetwork
  private lateinit var partyA: StartedMockNode
  private lateinit var partyB: StartedMockNode
  private lateinit var oracle: StartedMockNode
  private lateinit var notaryNode0: MockNetworkNotarySpec

  @Before
  fun setup() {
    notaryNode0 = MockNetworkNotarySpec(CordaX500Name("Notary-0", "London", "GB"))
    mockNetwork = MockNetwork(
      listOf(
        "com.lankydanblog"
      ),
      notarySpecs = listOf(notaryNode0)
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

    oracle =
      mockNetwork.createNode(
        MockNodeParameters(
          legalName = CordaX500Name(
            "Oracle",
            "London",
            "GB"
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
      GiveAwayStockFlow(
        "acn", 100, partyB.info.legalIdentities.first().name.toString()
      )
    )
    mockNetwork.runNetwork()
    println("done: ${future1.get()}")
  }
}