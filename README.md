# Sekiro [English](./README-EN.md)
SEKIRO是一个多语言的、分布式、网络拓扑无关的服务发布平台，通过书写各自语言的handler将功能发布到中心API市场，业务系统通过RPC的方式使用远端节点的能力。

更多介绍，请参考详细文档： [http://sekiro.iinti.cn/sekiro-doc/](http://sekiro.iinti.cn/sekiro-doc/)

各语言样例代码： [https://github.com/yint-tech/sekiro-samples](https://github.com/yint-tech/sekiro-samples)

安装包下载：[iinti sekiro-demo https://oss.iinti.cn/sekiro/sekiro-demo](https://oss.iinti.cn/sekiro/sekiro-demo)

## Sekiro是一个RPC框架
sekiro主要支持多节点的程序调用，所以他归属于RPC（Remote Procedure Call）框架：API管理、鉴权、分布式、负载均衡、跨语言

## Sekiro不是常规意义的RPC框架
通常情况下，在后端微服务下RPC框架主要用于拆分复杂业务模块，以及多节点集群提升单机性能瓶颈的能力。他们一般是单个机房下业务机器组，调用其他业务机器组。
Dubbo、springCloud、grpc便是目前市面上具有代表性的RPC解决方案，并且他们都是世界顶级的项目。然而Sekiro并不是解决这种常规RPC能力场景的方案。

Sekiro主要提供的功能是： 受限上下文环境下的功能外放，服务提供者（provider）运行在一个受限环境中，导致这个服务不能作为一个普通的算法方便的转移到内部服务，而此时我们的业务又希望可以使用这种受限环境下的功能。

* 一个加密算法，运行在客户端程序中，服务需要使用它但是没有完成这个算法的破译，可以通过sekiro注入代码到这个客户端，然后发布算法的API
* 一份数据，由于权限限制，仅允许在机构内网使用（机构来源ip检查），但是我们希望在外部服务调用。可以在机构内网书写sekiro客户端，实现API发布
* app（或者终端）程序，存在给C端客人使用的能力，但是我们希望在B端业务能够使用这个能力，那么通过Sekiro连接他，B端参数转发到app中，使用app代理调用能力：（这就是爬虫行业所谓的RPC爬虫）
* 服务提供者有一个能力需要给其他人使用，但是不希望交付代码，以及不希望泄漏这个服务对应的机器（IP地址等），那么可以通过sekiro发布服务。其他人只能通过sekiro使用能力，而无法了解这个能力的任何细节。
* 一个算法，需要复杂的计算环境，无法在外部服务轻松部署，那么可以使用Sekiro，将API寄身在可用环境上，然后export到外部

## 核心流程
1. 存在一个中心服务器：sekiro中心服务，他需要服务器端，可以被consumer和provider连接
2. 存在多种语言的客户端，java、js、python等，并且使用这些语言实现了和sekiro中心服务器的通信和API包装
3. 用户在各自语言中，使用sekiro客户端API编写handler，用于接受参数并且完成到真实能力的转发调用，连接sekiro服务和本地环境服务
4. 外部用户调用sekiro中心服务API，被sekiro服务转发到对应的客户端handler，获得调用结果后，原链路返回给用户


## 构建教程

- 安装Java
- 安装maven
- Linux/mac下，执行脚本：``build_demo_server.sh``，得到文件``target/sekiro-open-demo.zip``极为产出文件
- 运行脚本：``bin/sekiro.sh`` 或 ``bin/sekiro.bat``
- 文档： [http://127.0.0.1:5612/sekiro-doc](http://127.0.0.1:5612/sekiro-doc) 假设你的服务部署在本机：``127.0.0.1``

## 安装包

- [iinti sekiro-demo download](https://oss.iinti.cn/sekiro/sekiro-demo)



