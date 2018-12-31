# Corda Oracle

## Structure:

* app: Spring code
* cordapp-common: Corda common application code
* cordapp-node: Corda node application code
* cordapp-oracle: Corda oracle application code
* cordapp-contracts-and-states: Contracts and states

## Pre-requisites:

See https://docs.corda.net/getting-set-up.html.

## Usage

### Running the nodes:

Run the following gradle task to build the Corda nodes

* Windows: `gradlew.bat deployNodes`
* Unix: `./gradlew deployNodes`

Once built, go to the `build/nodes` directory and run `./runnodes` 

### Running the webservers:

Once the nodes are running, you can start the node webserver from the command line:

* Windows: `gradlew.bat runPartyAServer`
* Unix: `./gradlew runPartyAServer`

Both approaches use environment variables to set:

* `server.port`, which defines the HTTP port the webserver listens on
* `config.rpc.*`, which define the RPC settings the webserver uses to connect to the node

### Interacting with the nodes:

Send a stock via a post request

    `localhost:10011/stock/gift`

with a body like
```json
{
	"symbol":"acn",
	"amount":100,
	"recipient":"O=PartyB,L=London,C=GB"
}
```
Most importantly my blog can be found at [www.lankydanblog.com](https://lankydanblog.com) and you can follow me on Twitter at [@LankyDanDev](https://twitter.com/LankyDanDev)