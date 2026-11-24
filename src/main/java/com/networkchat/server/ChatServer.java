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
 * Demonstrates:
 * - Client-server architecture
 * - TCP/IP communication at application level
 * - Server IP addressing (loopback 127.0.0.1, LAN IP, and wildcard 0.0.0.0)
 * - Port binding using InetSocketAddress
 * - Java NIO ServerSocketChannel initialization and non-blocking mode configuration
 * - Graceful server lifecycle management (startup and shutdown)
 * - Robust error handling (e.g., BindException when port is already occupied)
 */
public class ChatServer implements AutoCloseable {

    private final String bindAddress;
    private final int port;
    private ServerSocketChannel serverChannel;
    private volatile boolean running = false;

    /**
     * Creates a ChatServer configured with default bind address (0.0.0.0) and default port.
     */
    public ChatServer() {
        this(NetworkConfig.DEFAULT_BIND_ADDRESS, NetworkConfig.DEFAULT_PORT);
    }

    /**
     * Creates a ChatServer with a specific port on default bind address (0.0.0.0).
     *
     * @param port the listening port (1 - 65535)
     */
    public ChatServer(int port) {
        this(NetworkConfig.DEFAULT_BIND_ADDRESS, port);
    }

    /**
     * Creates a ChatServer with specific bind address and port.
     *
     * @param bindAddress the IP address or hostname to bind to
     * @param port        the listening port (1 - 65535)
     */
    public ChatServer(String bindAddress, int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port number must be between 1 and 65535, given: " + port);
        }
        this.bindAddress = (bindAddress == null || bindAddress.isBlank())
                ? NetworkConfig.DEFAULT_BIND_ADDRESS
                : bindAddress.trim();
        this.port = port;
    }

    /**
     * Initializes and binds the ServerSocketChannel in non-blocking mode.
     *
     * @throws IOException   if an I/O error occurs during channel opening or binding
     * @throws BindException if the configured port is already in use
     */
    public synchronized void start() throws IOException {
        if (running) {
            System.out.println("[ChatServer] Server is already running on port " + port);
            return;
        }

        try {
            // 1. Open a new ServerSocketChannel (Java NIO)
            serverChannel = ServerSocketChannel.open();

            // 2. Configure non-blocking mode
            // Allows the server to check for connections without blocking the execution thread
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

    /**
     * Accepts a single incoming TCP client connection if available (non-blocking).
     *
     * @return SocketChannel if a client connected, or null if no connection is currently pending
     * @throws IOException if an I/O error occurs
     */
    public SocketChannel acceptConnection() throws IOException {
        if (!running || serverChannel == null || !serverChannel.isOpen()) {
            throw new IllegalStateException("Server is not currently running.");
        }
        // In non-blocking mode, accept() returns null immediately if no connection is pending
        SocketChannel clientChannel = serverChannel.accept();
        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            System.out.println("[ChatServer] Accepted TCP connection from: " + clientChannel.getRemoteAddress());
        }
        return clientChannel;
    }

    /**
     * Gracefully stops the server and closes the ServerSocketChannel, releasing the bound port.
     */
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

    // --- Getters for Member 3 (NIO Selector integration) and testing ---

    public ServerSocketChannel serverChannel() {
        return serverChannel;
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

    /**
     * Helper to display all available local and LAN IPv4 addresses.
     * Useful for demonstrating multi-machine LAN connections during assignment evaluation.
     */
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

    /**
     * Standalone main method allowing Member 1 to run and demonstrate the server setup,
     * port binding, LAN IP discovery, and TCP connection acceptance independently.
     */
    public static void main(String[] args) {
        int listeningPort = NetworkConfig.DEFAULT_PORT;
        String host = NetworkConfig.DEFAULT_BIND_ADDRESS;

        if (args.length >= 1) {
            try {
                listeningPort = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("[ChatServer] Invalid port number. Using default: " + NetworkConfig.DEFAULT_PORT);
            }
        }
        if (args.length >= 2) {
            host = args[1];
        }

        ChatServer server = new ChatServer(host, listeningPort);

        // Register shutdown hook for Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Shutdown Hook] Process termination requested.");
            server.stop();
        }, "chatserver-shutdown-hook"));

        try {
            server.start();
            System.out.println("[ChatServer] Listening for incoming client connections (Press Ctrl+C to exit)...");

            // Simple non-blocking polling demonstration for Member 1 viva/testing
            while (server.isRunning()) {
                SocketChannel client = server.acceptConnection();
                if (client != null) {
                    System.out.println("[ChatServer Demo] TCP handshake complete with: " + client.getRemoteAddress());
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (BindException e) {
            System.err.println("[ChatServer] Server could not start because the port is already bound.");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("[ChatServer] I/O Exception: " + e.getMessage());
            System.exit(1);
        }
    }
}
