package com.networkchat.server;

import com.networkchat.network.NetworkConfig;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;

/**
 * ChatServer - Member 1 responsibility: Server Setup, TCP/IP, Addressing and Ports.
 *
 * Demonstrates port binding using InetSocketAddress and port validation.
 */
public class ChatServer {

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
            // 1. Open a new ServerSocketChannel (Java NIO)
            serverChannel = ServerSocketChannel.open();

            // 2. Configure non-blocking mode
            serverChannel.configureBlocking(false);

            // 3. Encapsulate IP address and port using InetSocketAddress
            InetSocketAddress endpoint = new InetSocketAddress(bindAddress, port);

            // 4. Bind the channel's socket to the local address and port
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
            System.err.println("[ChatServer ERROR] Port " + port + " is already in use by another application.");
            throw e;
        }
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

    public ServerSocketChannel serverChannel() {
        return serverChannel;
    }
}
