# Intro

In this course we will be building pieces of an Orders microservice for the Reactive BBQ restaurant.

We will start with an initial state that has portions of the microservice complete.

Our job will be to complete the missing pieces, and modify them over time to try out various features of Akka Cluster Sharding.

# Existing Code

## Applications

There are 3 main application entry points for this project. Each application entry point includes one or more helper scripts to run that entry point. These scripts require you to work in an environment that can execute Bash scripts. However, these helper scripts simply wrap SBT commands, so if you aren't in an environment that supports Bash then you can run the SBT commands directly (or write your own helper scripts).

### Orders Service

The entrypoint for the Orders Service is in `Main.scala`. It pulls it's configuration from `resources/application.conf`.

There is a series of `runNodeX.sh` scripts where X is either 1, 2, or 3. These scripts run the microservice and apply a series of port overrides. The commands including the port overrides can be quite long. This simply allows you to run them more quickly wihout having to write out all of the overrides.

You can interact with the Orders Service using the `orders.sh` script.

To open an order run the following command, substituting a server name for <server> and a table number for <table>:

`./orders.sh "open <server> <table>"`

To find an order run the following command, substituting an appropriate Order Id for <orderId>:

`./orders.sh "find <orderId>"`

To add an item to the order, run the following command, substituting the Order Id for <orderId> the name of the item for <itemName> and any special instructions for <specialInstructions>:

`./orders.sh "add <orderId> <itemName> <specialInstructions>`

### Load Test

The entrypoint for the Load Test can be found in `LoadTest.scala`. It pulls it's configuration from `resources/loadtest.conf`

It simulates a collection of users going through a predefined set of steps (creating an order, retrieving the order, adding items to the order).

The load test assumes you are running all 3 instances of the application. If you want to change that you can override the ports in `loadtest.conf`

There is a `runLoadTest.sh` script to execute the Load Test. 

*NOTE:* The load test will push your system fairly hard. Keep that in mind if you are running off a laptop battery.

### Client

There is a small client application that can be found in `Client.scala`. It pulls it's configuration from `resources/client.conf`.

The client application allows you to make HTTP requests to the service using a simplified API (rather than sending full JSON requests). 

Alternatively you can use your favorite HTTP client (postman, curl etc). In that case you will have to understand the structure of the HTTP calls including their JSON payloads.

You can run the client application using `orders.sh`.
