package com.virjar.sekiro.server;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
public class SekiroServerApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        System.out.println("版本已经过期，开源版版本sekiro请使用开源demo server版本替换:\n" +
                "开源版本代码太丑陋，且容易出现性能问题，目前已经停止维护\n" +
                "请参考如下github文档，完成到demo版本的迁移\n" +
                "https://github.com/virjar/sekiro\n\n");
        System.out.println("!!! 非常重要 !!! \n当前版本将会在2021年7月31日之后正式下线，届时当前工程将会被销毁," +
                "且docker镜像将会无法拉取当前镜像。\n\n请各位用户尽快完成新版本Sekiro迁移\n\n");
        System.out.println("未完成迁移的，依然可以保持当前系统的使用，但是无法获取到使用支持，以及无法通过官方docker完成新服务器安装");
        SpringApplication.run(SekiroServerApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(SekiroServerApplication.class);
    }

}
