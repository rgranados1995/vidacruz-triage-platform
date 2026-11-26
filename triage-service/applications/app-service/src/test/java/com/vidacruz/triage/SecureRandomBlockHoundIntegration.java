package com.vidacruz.triage;

import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;

/**
 * The PostgreSQL driver signs in with SCRAM, whose nonce comes from {@code SecureRandom}. On Linux the JDK reads it
 * from {@code /dev/urandom} ({@code NativePRNG}), a file read that BlockHound reports when it happens on the Netty
 * thread of the connection. {@code /dev/urandom} never blocks, so only that read is allowed; any other blocking call
 * keeps failing the tests. On Windows the JDK does not read a file, which is why it only shows on Linux (the CI).
 */
public class SecureRandomBlockHoundIntegration implements BlockHoundIntegration {

    @Override
    public void applyTo(BlockHound.Builder builder) {
        builder.allowBlockingCallsInside("sun.security.provider.NativePRNG$RandomIO", "readFully");
    }
}
