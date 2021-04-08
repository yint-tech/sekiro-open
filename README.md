# sekiro

## 交流群
加微信：（virjar1）拉入微信交流群

## 商业版文档入口
[商业版](doc/readme.md)

## 开源版文档入口
[开源版](open-source-doc/README.md)

## 答问：Sekiro和NanoHttp的区别
[关于Sekiro和http转发的优势](qa_for_http_plan.md)

# 多项目友情链接(商务合作+v: virjar1 )

|项目|介绍|地址|
|--|--|--|
|echo|分布式代理ip共享集群|https://git.virjar.com/echo/echo|
|sekiro|基于长链接和代码注入的Android private API暴露框架|https://github.com/virjar/sekiro|
|ratel|Android重打包注入引擎|https://git.virjar.com/ratel/ratel-doc|
|zelda|app多开分身的另一种实现|https://github.com/virjar/zelda|
|geeEtacsufbo|极验滑块js代码脱壳-js控制流平坦化反混淆(最早使用AST解除JS流程混淆的项目)|https://github.com/virjar|
|thanos|java爬虫调度系统，让java爬虫重回巅峰！！(开发中)|https://github.com/virjar/thanos|


SEKIRO 是一个 android 下的 API 服务暴露框架，可以用在 app 逆向、app 数据抓取、android 群控等场景。

Sekiro 是我之前设计的群控系统 [Hermes](https://gitee.com/virjar/hermesagent) 的升级版，和其他群控框架相比的特点如下：

1. 对网络环境要求低，sekiro 使用长链接管理服务（可以理解为每个APP内置内网穿透功能），使得 Android 手机可以分布于全国各地，甚至全球各地。手机掺合在普通用户群体，方便实现反抓突破，更加适合获取下沉数据。
2. 不依赖 hook 框架，就曾经的 Hermes 系统来说，和 xposed 框架深度集成，在当今 hook 框架遍地开花的环境下，框架无法方便迁移。所以在 Sekiro 的设计中，只提供了 RPC 功能了。
3. 纯异步调用，在 Hermes 和其他曾经出现过的框架中，基本都是同步调用。虽然说签名计算可以达到上百 QPS，但是如果用来做业务方法调用的话，由于调用过程穿透到目标 app 的服务器，会有大量请求占用线程。系统吞吐存在上线(hermes 系统达到 2000QPS 的时候，基本无法横向扩容和性能优化了)。但是 Sekiro 全程使用 NIO，理论上其吞吐可以把资源占满。
4. client 实时状态，在 Hermes 系统我使用 http 进行调用转发，通过手机上报心跳感知手机存活状态。心跳时间至少 20s，这导致服务器调度层面对手机在线状态感知不及时，请求过大的时候大量转发调用由于 client 掉线 timeout。在 Sekiro 长链接管理下，手机掉线可以实时感知。不再出现由于框架层面机制导致 timeout
5. 群控能力，一台Sekiro服务器可以轻松管理上万个手机节点或者浏览器节点，且保证他们的RPC调用没有资源干扰。你不需要关心这些节点的物理网络拓扑结构。不需要管理这些手机什么时候上线和下线。如果你是用naohttpd方案，你可能需要为手机提供一个内网环境，然后配置一套内网穿透。一个内网一个机房，你需要管理哪些机房有哪些手机。当你的手机达到一百台之后，对应的物理网络环境就将会比较复杂，且需要开发一个独立系统管理了。如果你使用的时FridaRPC方案，你可能还需要为每几个手机配置一台电脑。然后电脑再配置内网穿透，这让大批量机器管理的拓扑结构更加复杂。这也会导致手机天然集中在一个机房，存在IP、基站、Wi-Fi、定位等环境本身对抗。
6. 多语言扩展能力。Sekiro的客户端lib库，目前已知存在Android(java)、IOS(objective-c)、js(浏览器)、易语言等多种客户端（不是所有的都是Sekiro官方实现）。Sekiro本身提供一个二进制协议（非常简单的二进制协议规则），只要你的语言支持socket(应该所有语言都支持)，那么你就可以轻松为Sekiro实现对应客户端。接入Sekiro，享受Sekiro本身统一机群管理的好处。在Sekiro的roadmap中，我们会基于frida Socket实现frida的客户端，完成Frida分析阶段的代码平滑迁移到Sekiro的生产环境。尽请期待
7. 客户端接入异步友好。Sekiro全程异步IO设计，这一方面保证整个框架的性能，另一方面更加贴近一般的app或者浏览器本身的异步环境。如果rpc调用用在签名计算上面，大部分签名仅仅是一个算法或者一个so的函数调用。那么同步调用就可以达到非常高的并发。但是如果你想通过rpc调用业务的API（如直接根据参数调用最网络框架的上层API，把参数编解码、加解密都，逻辑拼装都看作黑盒）。此时普通同步调用将会非常麻烦和非常消耗客户端性能。异步转同步的lock信号量机制、同步线程等待导致的线程资源占用和处理任务挤压等风险。FridaRPC支持异步，但是由于他的跨语言问题，并不好去构造异步的callback。目前nanohttpd或者FridaPRC，均大部分情况用在简单的签名函数计算上面。而Sekiro大部分用在上游业务逻辑的直接RPC场景上。
8. API友好（仅对AndroidAPI），我们为了编程方面和代码优雅，封装了一套开箱即用的API，基于SekiroAPI开发需求将会是非常快乐的。






