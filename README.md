# Sekiro [中文](./README.zh-CN.md)

SEKIRO is a multi-language, distributed, network topology-independent service publishing platform. By writing handlers in various programming languages, functionalities can be published to a central API marketplace, and business systems can utilize the capabilities of remote nodes through RPC (Remote Procedure Call).

For more detailed information, please refer to the documentation: [http://sekiro.iinti.cn/sekiro-doc/](http://sekiro.iinti.cn/sekiro-doc/)

Sample code for various languages: [https://github.com/yint-tech/sekiro-samples](https://github.com/yint-tech/sekiro-samples)

Installation package download: [iinti sekiro-demo https://oss.iinti.cn/sekiro/sekiro-demo](https://oss.iinti.cn/sekiro/sekiro-demo)

## Sekiro is an RPC Framework

Sekiro primarily supports multi-node program invocation, so it belongs to the RPC (Remote Procedure Call) framework category, offering: API management, authentication, distribution, load balancing, and cross-language support.

## Sekiro is Not a Conventional RPC Framework

Typically, in backend microservices, RPC frameworks are mainly used to decompose complex business modules and enhance single-machine performance bottlenecks through multi-node clusters. They are generally business machine groups within a single data center calling other business machine groups. Dubbo, Spring Cloud, and gRPC are representative RPC solutions currently available in the market, and they are all world-class projects. However, Sekiro is not a solution designed for such conventional RPC capability scenarios.

Sekiro's main functionality is: **exposing capabilities from restricted context environments**. Service providers run in a restricted environment, making it inconvenient to transfer these services as regular algorithms to internal services, while our business needs to utilize these capabilities from restricted environments.

* **Encryption algorithm**: Running in a client program, the service needs to use it but hasn't completed the algorithm's reverse engineering. You can inject code into this client through Sekiro and then publish the algorithm's API.
* **Restricted data**: Due to permission restrictions, data is only allowed to be used within an organization's intranet (checked by organization source IP), but we want to call it from external services. You can write a Sekiro client within the organization's intranet to implement API publishing.
* **App/Terminal programs**: Have capabilities for C-end customers, but we want B-end business to use these capabilities. Connect through Sekiro, forward B-end parameters to the app, and use the app to proxy call capabilities (this is what the crawler industry calls RPC crawling).
* **Service capability protection**: A service provider has a capability that needs to be used by others but doesn't want to deliver the code or leak the service's corresponding machine information (IP address, etc.). They can publish the service through Sekiro. Others can only use the capability through Sekiro without understanding any details of this capability.
* **Complex computing environments**: An algorithm requires a complex computing environment and cannot be easily deployed in external services. You can use Sekiro to host the API in an available environment and then export it externally.

## Core Workflow

1. There exists a central server: the Sekiro central service, which requires a server-side component that can be connected by both consumers and providers.
2. There are clients in multiple languages (Java, JavaScript, Python, etc.), and these languages have implemented communication with the Sekiro central server and API wrapping.
3. Users write handlers in their respective languages using the Sekiro client API to accept parameters and complete forwarding calls to real capabilities, connecting the Sekiro service with local environment services.
4. External users call the Sekiro central service API, which is forwarded by the Sekiro service to the corresponding client handler. After obtaining the call result, it returns to the user through the original link.

## Build Tutorial

* Install Java
* Install Maven
* On Linux/Mac, execute the script: `build_demo_server.sh` to get the output file `target/sekiro-open-demo.zip`
* Run the script: `bin/sekiro.sh` or `bin/sekiro.bat`
* Documentation: [http://127.0.0.1:5612/sekiro-doc](http://127.0.0.1:5612/sekiro-doc) (assuming your service is deployed on localhost: `127.0.0.1`)

## Installation Package

* [iinti sekiro-demo download](https://oss.iinti.cn/sekiro/sekiro-demo)

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=yint-tech/sekiro-open&type=Date)](https://www.star-history.com/#yint-tech/sekiro-open&Date)
