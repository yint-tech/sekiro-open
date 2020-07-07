package com.virjar.sekiro.client2;

/**
 * 第二代版本的Client，主要是为了提出netty的依赖。<br>
 * sekiro的业务场景下，只需要有一个tcp链接。此时不存在处理多个socket资源的需求。
 * 另外客户端大多数网络本来就是异步，也不需要考虑多线程的设计<br>
 * 所以我认为，BIO模式下的socket变成可以使得client依赖更加轻量、更容易理解、以及更加方便在其他平台扩展。<br>
 * 所以我们会逐步将client的底层实现方式由AIO替换为BIO
 */
public class SekiroClient2 {
}
