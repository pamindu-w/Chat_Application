package com.networkchat.server;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

/**
 * Acceptance test suite for Member 1:
 * 1. Server starts and binds to configured port.
 * 2. Client 1 can establish a TCP connection.
 * 3. Client 2 can also connect concurrently.
 * 4. Duplicate port binding throws BindException.
 * 5. Server shuts down cleanly and releases port immediately.
 */
public class ChatServerTest {

    public static void main(String[] args) {
        System.out.println(">>> Running Member 1 Acceptance Tests <<< \n");
        int passed = 0;
        int failed = 0;

        try {
            testServerStartupAndPortBinding();
            System.out.println("[PASS] Test 1: Server startup and port binding");
            passed++;
        } catch (Throwable t) {
            System.err.println("[FAIL] Test 1: " + t.getMessage());
            failed++;
        }

        try {
            testClientConnections();
            System.out.println("[PASS] Test 2: Concurrent client TCP connection establishment");
            passed++;
        } catch (Throwable t) {
            System.err.println("[FAIL] Test 2: " + t.getMessage());
            failed++;
        }

        try {
            testPortAlreadyInUse();
            System.out.println("[PASS] Test 3: Port already in use reports BindException");
            passed++;
        } catch (Throwable t) {
            System.err.println("[FAIL] Test 3: " + t.getMessage());
            failed++;
        }

        try {
            testCleanShutdownAndPortRelease();
            System.out.println("[PASS] Test 4: Server shuts down cleanly and releases port");
            passed++;
        } catch (Throwable t) {
            System.err.println("[FAIL] Test 4: " + t.getMessage());
            failed++;
        }

        System.out.println("\n=================================");
        System.out.println("Tests Run: " + (passed + failed) + ", Passed: " + passed + ", Failed: " + failed);
        System.out.println("=================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testServerStartupAndPortBinding() throws IOException {
        int testPort = 5051;
        try (ChatServer server = new ChatServer("127.0.0.1", testPort)) {
            server.start();
            if (!server.isRunning()) {
                throw new AssertionError("Server should be running");
            }
            if (!server.isOpen()) {
                throw new AssertionError("Server channel should be open");
            }
            if (server.localPort() != testPort) {
                throw new AssertionError("Expected port " + testPort + " but got " + server.localPort());
            }
        }
    }

    private static void testClientConnections() throws IOException, InterruptedException {
        int testPort = 5052;
        try (ChatServer server = new ChatServer("127.0.0.1", testPort)) {
            server.start();

            // Connect client 1
            try (SocketChannel client1 = SocketChannel.open()) {
                client1.connect(new InetSocketAddress("127.0.0.1", testPort));
                if (!client1.isConnected()) {
                    throw new AssertionError("Client 1 should be connected");
                }

                // Connect client 2
                try (SocketChannel client2 = SocketChannel.open()) {
                    client2.connect(new InetSocketAddress("127.0.0.1", testPort));
                    if (!client2.isConnected()) {
                        throw new AssertionError("Client 2 should be connected");
                    }

                    // Verify server accepts connections
                    Thread.sleep(50);
                    SocketChannel accepted1 = server.acceptConnection();
                    SocketChannel accepted2 = server.acceptConnection();

                    if (accepted1 == null || accepted2 == null) {
                        throw new AssertionError("Server should have accepted both client connections");
                    }
                    accepted1.close();
                    accepted2.close();
                }
            }
        }
    }

    private static void testPortAlreadyInUse() throws IOException {
        int testPort = 5053;
        try (ChatServer server1 = new ChatServer("127.0.0.1", testPort)) {
            server1.start();

            // Attempting to bind a second server to the same port must fail with BindException
            ChatServer server2 = new ChatServer("127.0.0.1", testPort);
            try {
                server2.start();
                throw new AssertionError("Expected BindException when binding duplicate port");
            } catch (BindException expected) {
                // Success: Port collision handled properly
            } finally {
                server2.stop();
            }
        }
    }

    private static void testCleanShutdownAndPortRelease() throws IOException {
        int testPort = 5054;
        ChatServer server = new ChatServer("127.0.0.1", testPort);
        server.start();
        if (!server.isRunning()) {
            throw new AssertionError("Server must be running");
        }

        server.stop();
        if (server.isRunning() || server.isOpen()) {
            throw new AssertionError("Server should be stopped and channel closed");
        }

        // Test that port was immediately released by starting another server on the same port
        try (ChatServer reboundServer = new ChatServer("127.0.0.1", testPort)) {
            reboundServer.start();
            if (!reboundServer.isRunning()) {
                throw new AssertionError("Port should be free and re-bindable after stop()");
            }
        }
    }
}
