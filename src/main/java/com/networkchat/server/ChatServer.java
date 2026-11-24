package com.networkchat.server;

import com.networkchat.network.NetworkConfig;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * ChatServer - Member 1 responsibility: Server Setup, TCP/IP, Addressing and Ports.
 *
 * Implements graceful shutdown lifecycle and socket resource release.
 */
public class ChatServer implements AutoCloseable {

    private final String bindAddress;
    private final int port;
    private ServerSocketChannel serverChannel;
    private volatile boolean running = false;

    public ChatServer() {
        this(NetworkConfig.DEFAULT_BIND_ADDRESS, NetworkConfig.DEFAULT_PORT);
    }

    public ChatServer(int port) {
        this(NetworkConfig.DEFAULT_BIND_ADDRESS, port);
    }

    public ChatServer(String bindAddress, int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port number must be between 1 and 65535, given: " + port);
        }
        this.bindAddress = (bindAddress == null || bindAddress.isBlank())
                ? NetworkConfig.DEFAULT_BIND_ADDRESS
                : bindAddress.trim();
        this.port = port;
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            InetSocketAddress endpoint = new InetSocketAddress(bindAddress, port);
            serverChannel.bind(endpoint);
            running = true;

            System.out.println("=================================================");
            System.out.println(" [ChatServer] Server Started Successfully!");
            System.out.println(" Protocol        : TCP (Transmission Control Protocol)");
            System.out.println(" Bound Address   : " + bindAddress);
            System.out.println(" Listening Port  : " + port);
            System.out.println(" Channel Mode    : Non-blocking (Java NIO)");
            System.out.println(" Local Endpoint  : " + serverChannel.getLocalAddress());
            System.out.println("=================================================");

        } catch (BindException e) {
            cleanup();
            System.err.println("[ChatServer ERROR] Port " + port + " is already in use by another application.");
            throw e;
        } catch (IOException e) {
            cleanup();
            System.err.println("[ChatServer ERROR] Failed to initialize server channel: " + e.getMessage());
            throw e;
        }
    }

    public SocketChannel acceptConnection() throws IOException {
        if (!running || serverChannel == null || !serverChannel.isOpen()) {
            throw new IllegalStateException("Server is not currently running.");
        }
        SocketChannel clientChannel = serverChannel.accept();
        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            System.out.println("[ChatServer] Accepted TCP connection from: " + clientChannel.getRemoteAddress());
        }
        return clientChannel;
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        System.out.println("[ChatServer] Shutting down server and releasing port " + port + "...");
        cleanup();
        System.out.println("[ChatServer] Server stopped successfully.");
    }

    private void cleanup() {
        running = false;
        if (serverChannel != null) {
            try {
                if (serverChannel.isOpen()) {
                    serverChannel.close();
                }
            } catch (IOException e) {
                System.err.println("[ChatServer WARN] Error while closing server channel: " + e.getMessage());
            } finally {
                serverChannel = null;
            }
        }
    }

    @Override
    public void close() {
        stop();
    }

    public String bindAddress() {
        return bindAddress;
    }

    public int port() {
        return port;
    }

    public int localPort() {
        if (serverChannel != null && serverChannel.isOpen()) {
            try {
                InetSocketAddress local = (InetSocketAddress) serverChannel.getLocalAddress();
                if (local != null) {
                    return local.getPort();
                }
            } catch (IOException ignored) {
            }
        }
        return port;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isOpen() {
        return serverChannel != null && serverChannel.isOpen();
    }

    public ServerSocketChannel serverChannel() {
        return serverChannel;
    }
}
