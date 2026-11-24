package com.networkchat.server;

import com.networkchat.network.NetworkConfig;

import java.io.IOException;
import java.net.BindException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.Enumeration;

/**
 * ChatServer - Member 1 responsibility: Server Setup, TCP/IP, Addressing and Ports.
 *
 * Adds network interface discovery for LAN IP demonstration.
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
            printNetworkInterfaces();

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

    public static void printNetworkInterfaces() {
        System.out.println("\nAvailable Network Interfaces on this machine:");
        System.out.println(" - Localhost (same computer test) : 127.0.0.1");
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                for (NetworkInterface netIf : Collections.list(interfaces)) {
                    if (netIf.isUp() && !netIf.isLoopback()) {
                        Enumeration<InetAddress> addresses = netIf.getInetAddresses();
                        for (InetAddress addr : Collections.list(addresses)) {
                            if (addr instanceof Inet4Address) {
                                System.out.println(" - LAN IP (" + netIf.getDisplayName() + "): " + addr.getHostAddress());
                            }
                        }
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println(" [!] Unable to enumerate network interfaces: " + e.getMessage());
        }
        System.out.println("-------------------------------------------------\n");
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
