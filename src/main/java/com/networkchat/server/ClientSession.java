package com.networkchat.server;

import com.networkchat.network.LineFramer;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Queue;

/** Per-client state holder. Selector-thread ownership is defined in docs/architecture.md. */
public final class ClientSession {
    private final SocketChannel channel;
    private final ByteBuffer inputBuffer;
    private final LineFramer framer;
    private final Queue<ByteBuffer> outgoing = new ArrayDeque<>();
    private SelectionKey key;
    private String username;

    public ClientSession(SocketChannel channel, int bufferSize) {
        this.channel = channel;
        this.inputBuffer = ByteBuffer.allocate(bufferSize);
        this.framer = new LineFramer();
    }

    public SocketChannel channel() { return channel; }
    public ByteBuffer inputBuffer() { return inputBuffer; }
    public LineFramer framer() { return framer; }
    public Queue<ByteBuffer> outgoing() { return outgoing; }
    public SelectionKey key() { return key; }
    public void key(SelectionKey key) { this.key = key; }
    public String username() { return username; }
    public void username(String username) { this.username = username; }
    public boolean authenticated() { return username != null; }
}
