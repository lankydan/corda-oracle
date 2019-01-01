I hang out in the Corda Slack channel quite a lot and try to answer questions when I can. A reasonable number of questions I have attempted to answer are related to Oracles. More specifically, when to use one. I feel like I can answer that, "Use an Oracle when you need to validate external data that can change frequently". I probably wrote an answer similar to that at some point. What I could not do though... Was tell someone how to implement one. Therefore, to correct that. I have written this post to learn how to implement one myself and share that knowledge with you and my future self.

## When to use an Oracle

Let's start with expanding on when to use an Oracle. As I touched on a minute ago, you should use an Oracle when you need to validate external data that can change frequently. This could be data such as exchange rates, stock prices or even whether my blog is currently up or down (although I haven't seen it be down yet!). I think the frequently part is important here. If data seldomly changes, it might be feasible to validate some data against an attachment containing the same sort of values the Oracle would be retrieving themselves. That is why validating data such as exchange rates, in my opinion, should only be done by an Oracle. All that being said, it really comes down to your particular use-case.

## How to use an Oracle

How does the Oracle do this validation? Well, that is up to you. But, it will likely follow these steps:
- Receive data from a node
- Retrieve external data 
- Validate the received data against the external data
- Provide a signature for the transaction

These are the steps that I think most Oracle implementations will comprise of. More steps could be added in and the validation that is done could be as complex or simple as the use-case demands. Although more steps could be added, I really doubt that there would be much use out of an Oracle that excluded any of the steps shown above.

All the steps shown above only show the process from the side of the Oracle. There is a bit more going on so I think a good diagram will help us out here. It will also go on to present the example that I will use for this post.

## FLOW CHART DIAGRAM

Quite a few of these steps are generic ones that are going to be made in whatever Flow you put together. In this section, I will expand and show the code involved to implement the Flow shown in the diagram. Therefore it is worth having a reasonable look at it... I also spent a lot of time making that look nice, so please look at it!!

Oh, another point before I continue. I want to highlight how helpful putting together sequence diagrams to model Corda Flows is. It really highlights who is involved, how many network hops need to be made and how much work each participant does. Furthermore, they go a good way of explaining what is going on to people who are only interested in the higher level processes that you are architecting and/or implementing.

### Client / Not the Oracle side

As I mentioned before, some of the code here is generic code that you are likely to put into any Flow that you write. I have shown it all so there is no ambiguity around what is happening, but I will only expand on points that need to be highlighted as they contain code specific to interacting with an Oracle.
```kotlin
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

  private fun oracle(): Party = serviceHub.networkMapCache.getPeerByLegalName(
    CordaX500Name(
      "Oracle",
      "London",
      "GB"
    )
  )
    ?: throw IllegalArgumentException("Oracle does not exist")

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

  private fun priceOfStock(): Double =
    serviceHub.cordaService(StockRetriever::class.java).getCurrent(symbol).price

  private fun state(party: Party, priceOfStock: Double): StockGiftState =
    StockGiftState(
      symbol = symbol,
      amount = amount,
      price = priceOfStock * amount,
      recipient = party
    )

  private fun notary(): Party = serviceHub.networkMapCache.notaryIdentities.first()

  @Suspendable
  private fun collectOracleSignature(
    transaction: SignedTransaction,
    oracle: Party
  ): SignedTransaction {
    val filtered = filteredTransaction(transaction, oracle)
    val signature = subFlow(CollectOracleStockPriceSignatureFlow(oracle, filtered))
    return transaction.withAdditionalSignature(signature)
  }
  
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
```
First, let's have a look at how the transaction is built:
```kotlin
private fun transaction(recipientParty: Party, oracle: Party): TransactionBuilder =
  TransactionBuilder(notary()).apply {
    val priceOfStock = priceOfStock()
    addOutputState(state(recipientParty, priceOfStock), StockContract.CONTRACT_ID)
    addCommand(
      GiveAway(symbol, priceOfStock),
      listOf(recipientParty, oracle).map(Party::owningKey)
    )
  }

private fun priceOfStock(): Double =
  serviceHub.cordaService(StockRetriever::class.java).getCurrent(symbol).price
```
There is not too much different here from how I would create a transaction that does not involve an Oracle. The only two differences are, retrieving the stock price from an external source (hidden inside the `StockRetriever` service) and including the signature of the Oracle in the Command. These code additions line up with the reasons for using an Oracle. External data is included in the transaction and the Oracle needs to verify it is correct. To prove that the Oracle has deemed a transaction valid, we need its signature.

We will look closer at retrieving the external data separately.

Next up is collecting the recipients signature:
```kotlin
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
```
Collecting the counterparty's signature is not really an uncommon step of a Flow but what is done differently here is the use of `CollectSignatureFlow` rather than the `CollectSignaturesFlow` which is normally used (noticed the "s" missing in the middle). That is due to requiring the Oracle's signature in the transaction. Calling the `CollectSignaturesFlow` will go off to retrieve signatures from all required signers, including the Oracle. This treats the Oracle like a "normal" participant. This is not what we want. Instead, we need to get the signature of the recipient and the Oracle individually and somewhat manually. The manual part is the use of `transaction.withAdditionalSignature`.

Now that the recipient has signed the transaction, the Oracle needs to sign it:
```kotlin
@Suspendable
private fun collectOracleSignature(
  transaction: SignedTransaction,
  oracle: Party
): SignedTransaction {
  val filtered = filteredTransaction(transaction, oracle)
  val signature = subFlow(CollectOracleStockPriceSignatureFlow(oracle, filtered))
  return transaction.withAdditionalSignature(signature)
}

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
```
Before sending the transaction to the Oracle, it is recommended to filter it to remove any information that is not needed by the Oracle. This prevents information that shouldn't be shared from being seen by the Oracle. Remember the Oracle is likely to be a node controlled by another organisation and is not a participant that you are attempting to share states and transactions with.

`SignedTransaction` provides the `buildFilteredTransaction` function that only includes objects that match the predicate passed in. In the example above it filters out everything but the `GiveAway` (command I created) command which must also have the Oracle as a signer.

This outputs a `FilteredTransaction` which is passed to `CollectOracleStockPriceSignatureFlow`:
```kotlin
@InitiatingFlow
class CollectOracleStockPriceSignatureFlow(
  private val oracle: Party,
  private val filtered: FilteredTransaction
) : FlowLogic<TransactionSignature>() {

  @Suspendable
  override fun call(): TransactionSignature {
    val session = initiateFlow(oracle)
    return session.sendAndReceive<TransactionSignature>(filtered).unwrap { it }
  }
}
```
All this code does is send the `FilteredTransaction` to the Oracle and awaits its signature. The code here could be put into the main Flow but it is quite nice to split code out when we can.

Finally, the `TransactionSignature` returned from the Oracle is added to the transaction in the same way the recipient's signature was added earlier. At this point, the transaction is ready to be committed as all the required signers have done their part.

### Oracle side

Now that we have covered the client side of the code, we need to have a look into how the Oracle validates the transaction. Below is the contents of the Oracle code:
```kotlin
@InitiatedBy(CollectOracleStockPriceSignatureFlow::class)
class OracleStockPriceSignatureResponder(private val session: FlowSession) : FlowLogic<Unit>() {

  @Suspendable
  override fun call() {
    val transaction = session.receive<FilteredTransaction>().unwrap { it }

    val key = key()

    val isValid = transaction.checkWithFun { element: Any ->
      when {
        element is Command<*> && element.value is GiveAway -> {
          val command = element.value as GiveAway
          (key in element.signers).also {
            validateStockPrice(
              command.symbol,
              command.price
            )
          }
        }
        else -> false
      }
    }

    if (isValid) {
      session.send(serviceHub.createSignature(transaction, key))
    } else {
      throw InvalidStockPriceFlowException("Transaction: ${transaction.id} is invalid")
    }
  }

  private fun key(): PublicKey = serviceHub.myInfo.legalIdentities.first().owningKey

  private fun validateStockPrice(symbol: String, price: Double) = try {
    serviceHub.cordaService(StockPriceValidator::class.java).validate(symbol, price)
  } catch (e: IllegalArgumentException) {
    throw InvalidStockPriceFlowException(e.message)
  }
}
```
Some of the code that should be here is hidden in the `StockPriceValidator` which retrieves the external stock price and compares it to the one passed to the Oracle. It doesn't have much code and its validation is basic so I won't elaborate on it. As it is short, I might as well show it now:
```kotlin
@CordaService
class StockPriceValidator(private val serviceHub: AppServiceHub) :
  SingletonSerializeAsToken() {

  fun validate(symbol: String, price: Double) =
    serviceHub.cordaService(StockRetriever::class.java).getCurrent(symbol).let {
      require(price == it.price) { "The price of $symbol is ${it.price}, not $price" }
    }
}
```
Back to the `OracleStockPriceSignatureResponder`. Firstly, `receive` is called to get the `FilteredTransaction` that the client sent. It is then checked using its `checkWithFun` function. This is a handy function that looks at each object and expects a `Boolean` in return. Using this, the transaction is deemed valid if all it contains are commands of `GiveAway` where the Oracle is the signer and most importantly checks that the external data contained in the command is correct. If you recall the code from earlier, the correct command and signers were passed in. The only remaining validation is on the external data. If that is all fine then the Oracle will accept the transaction and send its signature back to the client that requested it.

I chose to complete the validation via throwing exceptions (along with error messages) which are then propagated to the requesting party. I think this makes it easier on to understand what has gone wrong so it can be handled properly, rather than just a straight "failed validation" message. If the validation the Oracle is performing is complex, these error messages become even more valuable.

## Retrieving external data

You should have seen the `StockRetriever` class pop up twice now. It was used in both the requesting party and the Oracle. I have shared this code between both types of nodes (normal nodes and Oracles) but this might not be suitable for your own use-case. Furthermore, how you choose to retrieve your external data is up to you, I am just providing a possible solution.

The code can be found below:
```kotlin
@CordaService
class StockRetriever(serviceHub: AppServiceHub) :
  SingletonSerializeAsToken() {

  private val client = OkHttpClient()
  private val mapper = ObjectMapper()

  fun getCurrent(symbol: String): Stock {
    val response = client.newCall(request(symbol)).execute()
    return response.body()?.let {
      val json = it.string()
      require(json != "Unknown symbol") { "Stock with symbol: $symbol does not exist" }
      val tree = mapper.readTree(json)
      Stock(
        symbol = symbol,
        name = tree["companyName"].asText(),
        primaryExchange = tree["primaryExchange"].asText(),
        price = tree["latestPrice"].asDouble()
      )
    } ?: throw IllegalArgumentException("No response")
  }

  private fun request(symbol: String) =
    Request.Builder().url("https://api.iextrading.com/1.0/stock/$symbol/quote").build()
}
```
The `StockRetriever` is a nice little service that uses an `OkHttpClient` ([OkHttp](https://github.com/square/okhttp)) to make a HTTP request to an API (provided by [IEX Trading](https://iextrading.com/developer/docs/) using their [Java library](https://github.com/WojciechZankowski/iextrading4j)) that returns stock information when a stock symbol is provided. You can use whatever client you want to make the HTTP request. I saw this one in an example CorDapp and have taken it for my own. Personally, I'm too used to Spring so didn't really know any clients other than their `RestTemplate`.

Once the response is returned, it is converted into a `Stock` object and passed back to the function's caller. That's all folks.

## Conclusion

In conclusion, you should use an Oracle when your CorDapp requires frequently changing external data that needs to be validated before the transaction can be committed. Just like data held within states, external data is extremely important, probably the most important, as it is likely to determine the main contents of a transaction. Therefore all participants must feel comfortable that the data is correct and hasn't just come out of thin air. To achieve this, an Oracle will also retrieve the external data and validate it against what the transaction says the data should be. At this point, the Oracle will either sign the transaction or throw an exception and deem it invalid. The implementation side of this is reasonably straightforward as there are not many steps that need to be taken. Retrieve the data, send a `FilteredTransaction` to the Oracle containing the data where it will be validated. Yes, as you have read this post, you will know there is a bit more to it. But, for a basic Flow that is pretty much it. As I said somewhere near the start, how the Oracle does its validation can be as simple or complicated as required. Although, I think most will follow the same sort of process shown here.

Now for the main conclusion... In conclusion, you now have the knowledge to answer questions in the slack channel about Oracles or know where to send them if you can't!

The code used in this post can be found on my [GitHub](https://github.com/lankydan/corda-oracle).

If you found this post helpful, you can follow me on Twitter at [@LankyDanDev](https://twitter.com/LankyDanDev) to keep up with my new posts.
