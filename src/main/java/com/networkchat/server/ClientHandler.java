
package com.networkchat.server;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ClientHandler {

    private final SocketChannel clientChannel;
    private final Queue<ByteBuffer> outgoingMessages;

    private String username;
    private volatile boolean connected;

    // Used to notify the NIO Selector that new data is ready to send.
    private final Runnable selectorWakeup;

    public ClientHandler(SocketChannel clientChannel,
                         Runnable selectorWakeup) {

        this.clientChannel = clientChannel;
        this.selectorWakeup = selectorWakeup;

        this.outgoingMessages = new ConcurrentLinkedQueue<>();
        this.connected = true;
    }

    // Set the client's username after login.
    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public SocketChannel getClientChannel() {
        return clientChannel;
    }

    public boolean isConnected() {
        return connected && clientChannel.isOpen();
    }

    // Add a message to the outgoing queue.
    public void sendMessage(String message) {

        if (!isConnected()) {
            return;
        }

        // Newline is the message delimiter in our protocol.
        String formattedMessage = message + "\n";

        ByteBuffer buffer = ByteBuffer.wrap(
                formattedMessage.getBytes(StandardCharsets.UTF_8)
        );

        outgoingMessages.offer(buffer);

        // Notify the Selector that this client has data to send.
        selectorWakeup.run();
    }

    // Get the next outgoing message.
    // Called by the NIO Selector's event loop.
    public ByteBuffer peekOutgoingMessage() {
        return outgoingMessages.peek();
    }

    // Remove the current message after it has been fully written.
    public void removeOutgoingMessage() {
        outgoingMessages.poll();
    }

    public boolean hasPendingMessages() {
        return !outgoingMessages.isEmpty();
    }

    // Disconnect the client.
    public void disconnect() {

        connected = false;

        try {
            clientChannel.close();
        } catch (Exception e) {
            System.err.println(
                    "Error closing client connection: " + e.getMessage()
            );
        }

        outgoingMessages.clear();
    }
}
