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

[gist https://gist.github.com/lankydan/0cbcbfb617eedaa1365db74ea88a5553 ]

First, let's have a look at how the transaction is built:

[gist https://gist.github.com/lankydan/18f2af63aee6d04b82081601d2ca9892 /]

There is not too much different here from how I would create a transaction that does not involve an Oracle. The only two differences are, retrieving the stock price from an external source (hidden inside the `StockRetriever` service) and including the signature of the Oracle in the Command. These code additions line up with the reasons for using an Oracle. External data is included in the transaction and the Oracle needs to verify it is correct. To prove that the Oracle has deemed a transaction valid, we need its signature.

We will look closer at retrieving the external data separately.

Next up is collecting the recipients signature:

[gist https://gist.github.com/lankydan/b410dab17bfc2b9d82767abe6bf5ba90 /]

Collecting the counterparty's signature is not really an uncommon step of a Flow but what is done differently here is the use of `CollectSignatureFlow` rather than the `CollectSignaturesFlow` which is normally used (noticed the "s" missing in the middle). That is due to requiring the Oracle's signature in the transaction. Calling the `CollectSignaturesFlow` will go off to retrieve signatures from all required signers, including the Oracle. This treats the Oracle like a "normal" participant. This is not what we want. Instead, we need to get the signature of the recipient and the Oracle individually and somewhat manually. The manual part is the use of `transaction.withAdditionalSignature`.

Now that the recipient has signed the transaction, the Oracle needs to sign it:

[gist https://gist.github.com/lankydan/139f8842711c509a20a9c61b35a62e98 /]

Before sending the transaction to the Oracle, it is recommended to filter it to remove any information that is not needed by the Oracle. This prevents information that shouldn't be shared from being seen by the Oracle. Remember the Oracle is likely to be a node controlled by another organisation and is not a participant that you are attempting to share states and transactions with.

`SignedTransaction` provides the `buildFilteredTransaction` function that only includes objects that match the predicate passed in. In the example above it filters out everything but the `GiveAway` (command I created) command which must also have the Oracle as a signer.

This outputs a `FilteredTransaction` which is passed to `CollectOracleStockPriceSignatureFlow`:

[gist https://gist.github.com/lankydan/a0ef8386b1599b37da1538a4140fbc46 /]

All this code does is send the `FilteredTransaction` to the Oracle and awaits its signature. The code here could be put into the main Flow but it is quite nice to split code out when we can.

Finally, the `TransactionSignature` returned from the Oracle is added to the transaction in the same way the recipient's signature was added earlier. At this point, the transaction is ready to be committed as all the required signers have done their part.

### Oracle side

Now that we have covered the client side of the code, we need to have a look into how the Oracle validates the transaction. Below is the contents of the Oracle code:

[gist https://gist.github.com/lankydan/6d3cae38bcfcf9abd6959c9d6ed94a24 /]

Quite of a lot of the code that should be here is hidden in the `StockPriceValidator` which retrieves the external stock price and compares it to the one passed to the Oracle. It doesn't have much code and its validation is basic so I won't elaborate on it. As it is short, I might as well show it now:

[gist https://gist.github.com/lankydan/3d0ba47a2f865334f7e62d1b04dfa8b7 /]

Back to the `OracleStockPriceSignatureResponder`. Firstly, `receive` is called to get the `FilteredTransaction` that the client sent. It is then checked using its `checkWithFun` function. This is a handy function that looks at each object and expects a `Boolean` in return. Using this, the transaction is deemed valid if all it contains is commands of `GiveAway` where the Oracle is the signer and most importantly checks that the external data contained in the command is correct. If you recall the code from earlier, the correct command and signers were passed in. The only remaining validation is on the external data. If that is all fine then the Oracle will accept the transaction and send its signature back to the client that requested it.

I chose to complete the validation via throwing exceptions (along with error messages) which are then propagated to the requesting party. I think this makes it easier on to understand what has gone wrong so it can be handled properly, rather than just a straight "failed validation" message. If the validation the Oracle is performing is complex, these error messages become even more valuable.

## Retrieving external data

You should have seen the `StockRetriever` class pop up twice now. It was used in both the requesting party and the Oracle. I have shared this code between both types of nodes (normal nodes and Oracles) but this might not be suitable for your own use-case. Furthermore, how you choose to retrieve your external data is up to you, I am just providing a possible solution.

The code can be found below:

[gist https://gist.github.com/lankydan/75d9dd1f4862c12d4460f257032e8e2f /]

The `StockRetriever` is a nice little service that uses an `OkHttpClient` ([OkHttp](https://github.com/square/okhttp)) to make a HTTP request to an API (provided by [IEX Trading](https://iextrading.com/developer/docs/) using their [Java library](https://github.com/WojciechZankowski/iextrading4j)) that returns stock information when a stock symbol is provided. You can use whatever client you want to make the HTTP request. I saw this one in an example CorDapp and have taken it for my own. Personally, I'm too used to Spring so didn't really know any clients other than their `RestTemplate`.

Once the response is returned, it is converted into a `Stock` object and passed back to the function's caller. That's all folks.

## Conclusion

In conclusion, you should use an Oracle when your CorDapp requires external data that needs to be validated before the transaction can be committed. Just like data held within states, external data is extremely important, probably the most important, as it is likely to determine the main contents of a transaction. Therefore all participants must feel comfortable that the data is correct and hasn't just come out of thin air. To achieve this, an Oracle will also retrieve the external data and validate it against what the transaction says the data should be. At this point, the Oracle will either sign the transaction or throw an exception and deem it invalid. The implementation side of this is reasonably straightforward as there are not many steps that need to be taken. Retrieve the data, send a `FilteredTransaction` to the Oracle containing the data where it will be validated. Yes, as you have read this post, you will know there is a bit more to it. But, for a basic Flow that is pretty much it. As I said somewhere near the start, how the Oracle does its validation can be as simple or complicated as required. Although, I think most will follow the same sort of process shown here.

Now for the main conclusion... In conclusion, you now have the knowledge to answer questions in the slack channel about Oracles or know where to send them if you can't!

The code used in this post can be found on my [GitHub](https://github.com/lankydan/corda-oracle).

If you found this post helpful, you can follow me on Twitter at [@LankyDanDev](https://twitter.com/LankyDanDev) to keep up with my new posts.
