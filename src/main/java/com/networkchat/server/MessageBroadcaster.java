
package com.networkchat.server;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;

/**
 * Handles broadcasting messages to connected clients.
 *
 * Messages are added to each recipient's outgoing queue.
 * The NIO Selector thread is responsible for actual socket writes.
 */
public final class MessageBroadcaster {

    /**
     * Sends a protocol message to every connected client
     * except the original sender.
     *
     * @param clients      ClientManager containing connected sessions
     * @param sender       ClientSession that sent the message
     * @param protocolLine Formatted protocol message
     */
    public void queueForOthers(
            ClientManager clients,
            ClientSession sender,
            String protocolLine) {

        byte[] bytes = (protocolLine + "\n")
                .getBytes(StandardCharsets.UTF_8);

        for (ClientSession recipient : clients.sessions()) {

            // Do not send the message back to its sender.
            if (recipient == sender) {
                continue;
            }

            // Add a separate buffer to the recipient's outgoing queue.
            recipient.outgoing().add(ByteBuffer.wrap(bytes.clone()));

            // Enable OP_WRITE so the Selector can send pending messages.
            SelectionKey key = recipient.key();

            if (key != null && key.isValid()) {
                key.interestOps(
                        key.interestOps() | SelectionKey.OP_WRITE
                );
            }
        }
    }
}
