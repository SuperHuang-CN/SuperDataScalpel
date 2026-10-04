package cn.superhuang.superapigateway.runtime;

import io.netty.util.NetUtil;
import java.net.InetAddress;

/** Literal-only IPv4/IPv6 matcher: never performs DNS on the request path. */
public final class IpNetwork {
    private final byte[] address;
    private final int bits;

    public IpNetwork(String value) {
        if (value == null) throw new IllegalArgumentException("IP/CIDR 不能为空");
        String[] parts = value.trim().split("/", -1);
        address = NetUtil.createByteArrayFromIpAddressString(parts[0]);
        if (address == null || parts.length > 2) throw new IllegalArgumentException("仅支持 IPv4/IPv6 地址或 CIDR");
        try { bits = parts.length == 1 ? address.length * 8 : Integer.parseInt(parts[1]); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("CIDR 前缀长度无效"); }
        if (bits < 0 || bits > address.length * 8) throw new IllegalArgumentException("CIDR 前缀长度无效");
    }

    public boolean contains(InetAddress candidate) {
        if (candidate == null) return false;
        byte[] bytes = candidate.getAddress();
        if (bytes.length != address.length) return false;
        for (int i = 0; i < bits; i++) {
            int mask = 1 << (7 - i % 8);
            if ((bytes[i / 8] & mask) != (address[i / 8] & mask)) return false;
        }
        return true;
    }
}
