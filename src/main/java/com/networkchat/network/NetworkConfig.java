package com.networkchat.network;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Central configuration shared by the server, client and protocol implementations. */
public final class NetworkConfig {
    public static final String DEFAULT_BIND_ADDRESS = "0.0.0.0";
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 5000;
    public static final int BUFFER_SIZE = 4096;
    public static final int MAX_USERNAME_LENGTH = 24;
    public static final int MAX_MESSAGE_LENGTH = 512;
    public static final Charset CHARSET = StandardCharsets.UTF_8;

    private NetworkConfig() {
    }
}
