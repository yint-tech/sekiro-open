# Sekiro [中文](./README.md)
SEKIRO is a multi-language, distributed, network topology-independent service publishing platform. It enables the publishing of functionalities to the central API marketplace by writing handlers in various languages. Business systems use the abilities of remote nodes via RPC (Remote Procedure Call).

For more information, please refer to the detailed documentation: [http://sekiro.iinti.cn/sekiro-doc/](http://sekiro.iinti.cn/sekiro-doc/)

Sample code for various languages: [https://github.com/yint-tech/sekiro-samples](https://github.com/yint-tech/sekiro-samples)

Download the installation package: [iinti sekiro-demo https://oss.iinti.cn/sekiro/sekiro-demo](https://oss.iinti.cn/sekiro/sekiro-demo)

## Sekiro is an RPC Framework
Sekiro primarily supports multi-node program invocation, thus it belongs to the RPC (Remote Procedure Call) framework category: API management, authentication, distribution, load balancing, and cross-language.

## Sekiro is Not a Conventional RPC Framework
Usually, in backend microservices, RPC frameworks are mainly used to decompose complex business modules and enhance single-machine performance bottleneck capabilities through multi-node clusters. They are typically business machine groups within a single machine room, calling other business machine groups. Dubbo, SpringCloud, and gRPC are representative RPC solutions on the market, and they are all world-leading projects. However, Sekiro is not a solution for such conventional RPC capability scenarios.

Sekiro's main function is to provide external functionality in a restricted context. The service provider (provider) operates in a restricted environment, making this service inconvenient to be transferred to internal services as a conventional algorithm. At this point, our business hopes to use this functionality in a restricted environment.

* An encryption algorithm runs in a client program. The service needs to use it but has not completed the decryption of this algorithm. You can use Sekiro to inject code into this client and then publish the algorithm's API.
* A piece of data, due to permission restrictions, is only allowed to be used within the organization's intranet (checked by the organization's source IP), but we hope to call it in external services. You can write a Sekiro client in the organization's intranet to implement API publishing.
* An app (or terminal) program has capabilities for C-end customers, but we hope that B-end business can use this capability. Then connect it through Sekiro, and forward B-end parameters to the app, using the app to proxy call capabilities: (This is what the crawler industry calls an RPC crawler).
* The service provider has a capability that needs to be used by others, but does not want to deliver the code, and does not want to leak this service's corresponding machine (IP address, etc.). Then you can publish the service through Sekiro. Others can only use the capability through Sekiro and cannot understand any details of this capability.
* An algorithm that requires a complex computing environment and cannot be easily deployed in external services can use Sekiro to host the API in an available environment, and then export it to the outside.

## Core Process
1. There is a central server: the Sekiro central service. It needs to be server-side and can be connected by consumers and providers.
2. There are clients in multiple languages, such as Java, JS, Python, etc., and these languages have implemented communication with the Sekiro central server and API wrapping.
3. Users write handlers in their respective languages using the Sekiro client API to accept parameters and complete the forwarding call to the real capability, connecting the Sekiro service and local environment services.
4. External users call the Sekiro central service API, which is forwarded by the Sekiro service to the corresponding client handler. After obtaining the call result, it returns to the user via the original link.

## Build Tutorial

- Install Java
- Install Maven
- On Linux/mac, execute the script: ``build_demo_server.sh``, and the output file is ``target/sekiro-open-demo.zip``.
- Run the script: ``bin/sekiro.sh`` or ``bin/sekiro.bat``
- Documentation: [http://127.0.0.1:5612/sekiro-doc](http://127.0.0.1:5612/sekiro-doc) Assuming your service is deployed on localhost: ``127.0.0.1``

## Installation Package

- [iinti sekiro-demo download](https://oss.iinti.cn/sekiro/sekiro-demo)
