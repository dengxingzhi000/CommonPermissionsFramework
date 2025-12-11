package com.frog.gateway.support.ip;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Optional;

/**
 * Minimal CIDR matcher supporting IPv4 and IPv6.
 * 支持 IPv4 和 IPv6 的极简 CIDR 匹配器。
 */
@Slf4j
public record IpSubnet(boolean matchAll, InetAddress networkAddress, int prefixLength) {

    public IpSubnet {
        if (matchAll) {
            networkAddress = null;
            prefixLength = 0;
        } else {
            if (networkAddress == null) {
                throw new IllegalArgumentException("networkAddress must not be null");
            }
            validatePrefix(prefixLength, networkAddress.getAddress().length);
            byte[] normalizedNetwork = trimAddress(networkAddress.getAddress(), prefixLength);
            try {
                networkAddress = InetAddress.getByAddress(normalizedNetwork);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Invalid network address", e);
            }
        }
    }

    public static Optional<IpSubnet> parse(String spec) {
        if (!StringUtils.hasText(spec)) {
            return Optional.empty();
        }
        String trimmed = spec.trim();
        if ("*".equals(trimmed)) {
            return Optional.of(new IpSubnet(true, null, 0));
        }

        int slashIndex = trimmed.indexOf('/');
        String ipPart = slashIndex > 0 ? trimmed.substring(0, slashIndex) : trimmed;
        String prefixPart = slashIndex > 0 ? trimmed.substring(slashIndex + 1) : null;

        try {
            InetAddress address = InetAddress.getByName(ipPart);
            int prefix = prefixPart == null
                    ? address.getAddress().length * 8
                    : Integer.parseInt(prefixPart.trim());
            validatePrefix(prefix, address.getAddress().length);
            byte[] normalizedNetwork = trimAddress(address.getAddress(), prefix);
            InetAddress normalized = InetAddress.getByAddress(normalizedNetwork);
            return Optional.of(new IpSubnet(false, normalized, prefix));
        } catch (UnknownHostException | IllegalArgumentException ex) {
            log.warn("Ignoring invalid CIDR specification '{}': {}", spec, ex.getMessage());
            return Optional.empty();
        }
    }

    private static void validatePrefix(int prefix, int addressLength) {
        if (prefix < 0 || prefix > addressLength * 8) {
            throw new IllegalArgumentException("prefix out of range: " + prefix);
        }
    }

    private static byte[] trimAddress(byte[] addressBytes, int prefix) {
        byte[] result = Arrays.copyOf(addressBytes, addressBytes.length);
        int fullBytes = prefix / 8;
        int remainingBits = prefix % 8;
        if (remainingBits != 0 && fullBytes < result.length) {
            int mask = (-1) << (8 - remainingBits);
            result[fullBytes] = (byte) (result[fullBytes] & mask);
            fullBytes++;
        }
        for (int i = fullBytes; i < result.length; i++) {
            result[i] = 0;
        }
        return result;
    }

    public boolean matches(InetAddress address) {
        if (matchAll) {
            return true;
        }
        if (address == null) {
            return false;
        }
        byte[] candidate = address.getAddress();
        byte[] network = networkAddress.getAddress();
        if (candidate.length != network.length) {
            return false;
        }
        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (candidate[i] != network[i]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = (-1) << (8 - remainingBits);
        return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
    }
}
