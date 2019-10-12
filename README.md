# sekiro

SEKIRO 是一个android下的API服务暴露框架，可以用在app逆向、app数据抓取、android群控等场景。

Sekiro是我之前设计的群控系统 [Hermes](https://gitee.com/virjar/hermesagent) 的升级版，和其他群控框架相比的特点如下：

1. 对网络环境要求低，sekiro使用长链接管理服务，使得Android手机可以分布于全国各地，甚至全球各地。手机掺合在普通用户群体，方便实现反抓突破，更加适合获取下沉数据。
2. 不依赖hook框架，就曾经的Hermes系统来说，和xposed框架深度集成，在当今hook框架遍地开花的环境下，框架无法方便迁移。所以在Sekiro的设计中，只提供了RPC功能了。
3. 纯异步调用，在Hermes和其他曾经出现过的框架中，基本都是同步调用。虽然说签名计算可以达到上百QPS，但是如果用来做业务方法调用的话，由于调用过程穿透到目标app的服务器，会有大量请求占用线程。系统吞吐存在上线(hermes系统达到2000QPS的时候，基本无法横向扩容和性能优化了)。但是Sekiro全程使用NIO，理论上其吞吐可以把资源占满。
4. 等等


# 部署流程

部署区分服务器端部署和客户端部署，服务器使用SpringBoot实现，占三个端口(``server.port: http管理端，同步http``| ``natServerPort:手机nat穿透端，和手机长链接``| ``natHttpServerPort:NIO的http服务端，只提供RPC调用入口``)
手机端一般附加在apk代码逻辑中。

## 服务端部署

两种方式，基于源码部署和jar包运行

### 源码部署服务器
执行脚本 ``./runProd.sh``即可，不过在服务器，由于gradle项目存在Android端，所以需要配置Androidsdk环境


```
#安装sdkman
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
#安装gradle
sdk install gradle 4.4

#下载并解压android sdk
wget http://dl.google.com/android/android-sdk_r24.4.1-linux.tgz
tar -zvxf android-sdk_r24.4.1-linux.tgz
#设置环境变量

echo "export ANDROID_HOME=path/to/android-sdk-linux" >> /etc/profile
echo "export PATH=$ANDROID_HOME/tools:$PATH"
source /etc/profile

#安装sdk
android list sdk --all
android update sdk -u --all --filter 7  #选择对应sdk的编号，我这边装的27.0.3 对应编号7

# 如果出现
# Failed to install the following Android SDK packages as some licences have not been accepted.
#      build-tools;28.0.3 Android SDK Build-Tools 28.0.3
#   To build this project, accept the SDK license agreements and install the missing components using the Android Studio SDK Manager.
#   Alternatively, to transfer the license agreements from one workstation to another, see http://d.android.com/r/studio-ui/export-licenses.html
# 那么执行
android update sdk -u --all --filter itemId(在--all里面，缺少那个选择那个)
```

之后再次执行脚本  ``./runProd.sh``即可

### jar包部署

1. 当前目录执行代码: ``./gradlew sekiro-server:bootJar``  即可在 `` sekiro-server/build/libs/sekiro-server-0.0.1-SNAPSHOT.jar``找到all-in-one的jar包
2. 通过命令 ``nohup java -jar sekiro-server/build/libs/sekiro-server-0.0.1-SNAPSHOT.jar >/dev/null 2>&1  &`` 即可启动服务器

### 端口配置

在``sekiro-server/src/main/resources/appliation.properties``中可以配置三个服务端端口


## client使用

需要注意，client api发布在maven仓库，而非jcenter仓库
```
dependencies {
    implementation 'com.virjar:sekiro-api:1.0.0'
}
```

然后即可在apk代码中书写调用服务逻辑:
```
SekiroClient.start("sekiro.virjar.com",clientId,"sekiro-demo")
        .registerHandler("clientTime",new SekiroRequestHandler(){
            @Override
            public void handleRequest(SekiroRequest sekiroRequest,SekiroResponse sekiroResponse){
                    sekiroResponse.success(" now:"+System.currentTimeMillis()+ " your param1:" + sekiroRequest.getString("param1"));
            }
        });

```
安装apk到手机，并打开，然后可以通过服务器访问这个接口

```
http://sekiro.virjar.com/groupList

{"status":0,"message":null,"data":["sekiro-demo"],"clientId":null,"ok":true}


http://sekiro.virjar.com/natChannelStatus?group=sekiro-demo
{"status":0,"message":null,"data":["2e77bbfa_869941041217576"],"clientId":null,"ok":true}


http://sekiro.virjar.com/invoke?group=sekiro-demo&action=clientTime&param1=%E8%87%AA%E5%AE%9A%E4%B9%89%E5%8F%82%E6%95%B0

{"clientId":"2e77bbfa_869941041217576","data":"process: com.virjar.sekiro.demoapp : now:1570546873170 your param1:自定义参数","ok":true,"status":0}

http://sekiro.virjar.com/asyncInvoke?group=sekiro-demo&action=clientTime&param1=%E8%87%AA%E5%AE%9A%E4%B9%89%E5%8F%82%E6%95%B0

{"clientId":"2e77bbfa_869941041217576","data":"process: com.virjar.sekiro.demoapp : now:1570897005965 your param1:自定义参数","ok":true,"status":0}
```
client demo在``app-demo``子工程可以看到，直接运行app-demo，即可在 sekiro.virjar.com看到你的设备列表

# 在类似xposed的代码注入框架中使用Sekiro

Sekiro本身不提供代码注入功能，不过Sekiro一般需要和代码注入框架配合产生作用，如和Xposed配合，可以方便调用app内部私有API，一般情况下，在Xposed入口启动Sekiro，然后接受服务器指令,并将参数转发到app内部。

Sekiro调用真实apk的例子稍后提供


# 服务器异步http

sekiro框架在http服务模块，提供了两个http端口，分别为BIO和NIO模式，其中BIO模式提供给tomcat容器使用，为了方便springBoot集成。另一方面，NIO提供给调用转发模块，NIO转发过程并不会占用线程池资源，理论上只对连接句柄和CPU资源存在瓶颈。

sekiro的这两个服务分别占用两个不同端口，分别为:
```
#tomcat 占用端口
server.port=5602
#长链接服务占用端口
natServerPort=5600
# 异步http占用端口
natHttpServerPort=5601
```

同时两个请求的uri也有一点差异，分别为,

BIO:  http://sekiro.virjar.com/invoke?group=sekiro-demo&action=clientTime&param1=%E8%87%AA%E5%AE%9A%E4%B9%89%E5%8F%82%E6%95%B0

NIO:  http://sekiro.virjar.com/asyncInvoke?group=sekiro-demo&action=clientTime&param1=%E8%87%AA%E5%AE%9A%E4%B9%89%E5%8F%82%E6%95%B0

可以看到sekiro的demo网站中，都是占用了统一个端口，这是因为存在ngnix转发，你可以参照如下配置实现这个效果:
```
upstream sekiro_server {
  server 127.0.0.1:5602;
}

upstream sekiro_nio {
  server 127.0.0.1:5601;
}

server {
  listen 0.0.0.0:80;
  listen [::]:80;
  server_name sekiro.virjar.com;
  server_tokens off;

  real_ip_header X-Real-IP;
  real_ip_recursive off;


location / {
    client_max_body_size 0;
    gzip off;

    proxy_read_timeout      300;
    proxy_connect_timeout   300;
    proxy_redirect          off;

    proxy_http_version 1.1;

    proxy_set_header    Host                $http_host;
    proxy_set_header    X-Real-IP           $remote_addr;
    proxy_set_header    X-Forwarded-For     $proxy_add_x_forwarded_for;
    proxy_set_header    X-Forwarded-Proto   $scheme;

    proxy_pass http://sekiro_server;
  }

location /asyncInvoke {
    client_max_body_size 0;
    gzip off;

    proxy_read_timeout      300;
    proxy_connect_timeout   300;
    proxy_redirect          off;

    proxy_http_version 1.1;

    proxy_set_header    Host                $http_host;
    proxy_set_header    X-Real-IP           $remote_addr;
    proxy_set_header    X-Forwarded-For     $proxy_add_x_forwarded_for;
    proxy_set_header    X-Forwarded-Proto   $scheme;

    proxy_pass http://sekiro_nio;
  }
}
```

强烈建议使用NIO接口访问调用服务