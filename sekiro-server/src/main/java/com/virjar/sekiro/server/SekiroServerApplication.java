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
                "https://github.com/virjar/sekiro");
        SpringApplication.run(SekiroServerApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(SekiroServerApplication.class);
    }

}
