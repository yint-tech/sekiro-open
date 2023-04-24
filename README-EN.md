# Sekiro
SEKIRO is a multi-language, distributed, network-topology-agnostic service publishing platform. 

It publishes functions to a central API marketplace by writing handlers in different languages, 

and business systems can use the capabilities of remote nodes via RPC.

For more information, please refer to the detailed documentation: https://iinti.cn/sekiro/


## Sekiro is an RPC framework 

that mainly supports multi-node program calls, so it belongs to the RPC (Remote Procedure Call) framework: 

API management, authentication, distribution, load balancing, and cross-language support.

## However, Sekiro is not a conventional RPC framework. 

In general, RPC frameworks are used in backend microservices to split complex business modules and to enhance the performance bottleneck of a single machine in a multi-node cluster. 

They are usually a group of business machines in a single data center that call other business machines. Dubbo, springCloud, and gRPC are representative RPC solutions on the market, and they are all world-class projects.

However, Sekiro is not a solution for this typical RPC capability scenario.

Sekiro mainly provides the function of exposing restricted context environments. 

Providers run in a restricted environment, which means that the service cannot be easily transferred as a normal algorithm to an internal service. However, businesses want to use this restricted environment's function.

- For example, if an encryption algorithm runs in a client program and the service needs to use it but has not cracked the algorithm, Sekiro can inject code into the client and then publish the algorithm's API. 
- Similarly, if a piece of data is restricted for internal network use only, but the business wants to use it for external services, a Sekiro client can be written within the internal network to implement API publishing. 
- If an app or terminal program has the ability to be used by C-end customers, but the business wants to use this ability for B-end, it can be connected through Sekiro, with B-end parameters forwarded to the app for proxy calling capability. 
- Service providers may have a capability that needs to be used by others, but they do not want to deliver the code or leak the machine (IP address, etc.) that corresponds to the service. In this case, they can use Sekiro to publish the service. Others can only use the capability through Sekiro without knowing any details of it.

## The core process involves a central server: 

1. the Sekiro central service, which needs a server-side connection that can be connected by consumers and providers. 
2. Clients are available in multiple languages, such as Java, JS, and Python, and these languages are used to communicate with the Sekiro central server and package the API. 
3. Users can write handlers using the Sekiro client API in their respective languages to accept parameters and complete the forwarding call to the real capability, connecting the Sekiro service and the local environment service. 
4. External users call the Sekiro central service API, which is then forwarded to the corresponding client handler by the Sekiro service. After obtaining the call result, the original link is returned to the user.

# To build Sekiro,
- Java and Maven need to be installed. 

- After executing the script build_demo_server.sh on Linux/mac, the target/sekiro-open-demo.zip file is produced. 
- Then, execute the script bin/sekiro.sh or bin/sekiro.bat to run the service. 
- The documentation is available at http://127.0.0.1:5612/sekiro-doc if the service is deployed on your local machine at 127.0.0.1.