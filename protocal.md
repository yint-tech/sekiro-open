# 二进制通信协议

## 协议格式
如果你的编程语言不是java，依然希望接入sekrio，那么你需要自己实现sekiro的协议。以下文档可能对你有帮助

报文结构:
```cpp
struct SekiroPacket {
    //数据包长度，
    __int32_t packet_length;
    //消息类型，
    int8_t message_type;
    //消息id，每次递增，作为请求和响应关联使用。这个id是服务器端产生，客户端只需要使用
    int64_t serial_number;
    //扩展数据长度
    int8_t ext_length;
    //扩展数据,UTF8的字符串编码
    char *ext;
    //body,
    char *payload;
};
```

需要注意，数字编码方式为大端！！！请以大端方式编码所有数字

### packet_length计算算法:
packet_length =  sizeof(int8_t) + sizeof(int64_t) + sizeof(int8_t) + ext_length + payload_length

### message_type

```
    /**
     * 心跳消息
     */
    public static final byte TYPE_HEARTBEAT = 0x07;

    /**
     * 设备注册到服务器
     */
    public static final byte C_TYPE_REGISTER = 0x01;

    /**
     * 服务器invoke转发
     */
    public static final byte TYPE_INVOKE = 0x02;
```
也即，目前通信协议中，消息类型只有上诉三种消息类型


### serial_number
服务器每次发送一个invoke指令，都会带有一个64为长度的id，作为请求id。客户端处理完成之后，需要通过这个id关联响应。
如果客户端是同步IO，那么基本不需要考虑这个id的管理，回写的时候原路写回即可

### ext
ext是一个utf8的字符串，在不同时刻有不同含义，具体参见消息类型。请注意ext长度不能超过512字节

### payload
在invoke的时候，传输的数据内容，请求可能是key-value-pair 也可能是json字符串


## 客户端注册
客户端为服务器提供服务之前，需要客户端先行注册长链接到服务器，注册之后，需要有一个流程。告诉服务器客户端的身份，此时服务器才能将这个长链接标记为一个可以被调度的资源。
```
SekiroPacket sekiroPacket();
sekiroPacket.message_type = C_TYPE_REGISTER;
sekiroPacket.ext = clientId + "@" + group;

//其他字段为空
```


其中 clientID是客户端的唯一标识，如果没有特殊需要，可以使用随机数。
group为你的业务id，具体解释参见sekiro首页文档。

客户端注册完成之后，服务器不会返回任何响应。此时不需要考虑服务器是否收到请求。

## 客户端心跳
客户端注册完成之后，需要每隔20s发送心跳包给服务器，服务器收到客户端的心跳包之后，会马上回复一个ACK。

*重要*
如果客户端连续40s都没有收到服务器的报文，那么需要客户端主动关闭链接，重连服务器。

客户端心跳包格式:

```
SekiroPacket sekiroPacket();
sekiroPacket.message_type = TYPE_HEARTBEAT;

//其他字段为空
```

## 服务器业务请求：

长链接保持之后，会不间断的收到服务器的业务请求。message_type为：``TYPE_INVOKE``

此时客户端解码: payload字段，编码方式为UTF8,内容为json

之后需要将请求转发给业务模块，处理后得到请求的响应。之后回写给服务器

## 回写处理结果给服务器
```cpp
SekiroPacket sekiroPacket();
sekiroPacket.message_type = TYPE_INVOKE;
sekiroPacket.serial_number = request.serial_number;//这个序列号是服务器调用的时候传递的
sekiroPacket.payload = data;//计算结果，一般建议为json字符串的字节流，编码方式UTF8
sekiroPacket.ext = contentType;//数据的IMEI-TYPE,一般设定为: application/json; charset=utf-8
```

