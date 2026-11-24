package com.networkchat.server;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Queues protocol lines; the selector thread remains responsible for actual channel writes. */
public final class MessageBroadcaster {
    public void queueForOthers(ClientManager clients, ClientSession sender, String protocolLine) {
        byte[] bytes = protocolLine.getBytes(StandardCharsets.UTF_8);
        for (ClientSession recipient : clients.sessions()) {
            if (recipient != sender) {
                recipient.outgoing().add(ByteBuffer.wrap(bytes.clone()));
                if (recipient.key() != null) {
                    recipient.key().interestOps(recipient.key().interestOps() | java.nio.channels.SelectionKey.OP_WRITE);
                }
            }
        }
    }
}
