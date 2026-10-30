package com.studysnap.backend.config;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Test-JVM resolver that records every host name lookup and delegates to the built-in resolver, so a test can
 * prove that attacker-controlled header text never reaches name resolution. Installed by
 * META-INF/services/java.net.spi.InetAddressResolverProvider.
 */
public class RecordingInetAddressResolverProvider extends InetAddressResolverProvider {
    static final Set<String> LOOKED_UP = ConcurrentHashMap.newKeySet();

    @Override
    public InetAddressResolver get(Configuration configuration) {
        InetAddressResolver builtin = configuration.builtinResolver();
        return new InetAddressResolver() {
            @Override
            public Stream<InetAddress> lookupByName(String host, LookupPolicy policy) throws UnknownHostException {
                LOOKED_UP.add(host);
                return builtin.lookupByName(host, policy);
            }

            @Override
            public String lookupByAddress(byte[] address) throws UnknownHostException {
                return builtin.lookupByAddress(address);
            }
        };
    }

    @Override
    public String name() {
        return "recording-test-resolver";
    }
}
