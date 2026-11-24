package com.networkchat.network;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Per-connection newline framer. It handles split and combined TCP reads. */
public final class LineFramer {
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

    public List<String> accept(ByteBuffer source) {
        List<String> lines = new ArrayList<>();
        while (source.hasRemaining()) {
            byte value = source.get();
            if (value == '\n') {
                byte[] bytes = pending.toByteArray();
                pending.reset();
                int length = bytes.length > 0 && bytes[bytes.length - 1] == '\r' ? bytes.length - 1 : bytes.length;
                if (length > NetworkConfig.MAX_MESSAGE_LENGTH + NetworkConfig.MAX_USERNAME_LENGTH + 16) {
                    throw new IllegalArgumentException("Protocol line is too long");
                }
                lines.add(new String(bytes, 0, length, NetworkConfig.CHARSET));
            } else {
                pending.write(value);
                if (pending.size() > NetworkConfig.MAX_MESSAGE_LENGTH + NetworkConfig.MAX_USERNAME_LENGTH + 16) {
                    throw new IllegalArgumentException("Protocol line is too long");
                }
            }
        }
        return lines;
    }
}
