package cn.iinti.sekiro3.open.utils;


import lombok.Getter;
import org.apache.commons.lang3.math.NumberUtils;

@Getter
public class IpAndPort {
    private final String ip;
    private final Integer port;
    private final String ipPort;

    private boolean illegal = false;

    public IpAndPort(String ip, Integer port) {
        this.ip = ip;
        this.port = port;
        this.ipPort = ip + ":" + port;
        this.illegal = true;
    }

    public IpAndPort(String ipPort) {
        this.ipPort = ipPort.trim();
        if (!ipPort.contains(":")) {
            ip = "";
            port = 0;
            return;
        }
        String[] split = ipPort.split(":");
        ip = split[0].trim();
        port = NumberUtils.toInt(split[1].trim(), -1);
        illegal = port > 0 && port <= 65535;
    }

    @Override
    public String toString() {
        return ipPort;
    }
}
