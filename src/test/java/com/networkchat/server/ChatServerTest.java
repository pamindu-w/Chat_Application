package com.networkchat.server;

import java.io.IOException;
import java.net.BindException;

/**
 * Initial acceptance tests for Member 1: Server startup, binding, and error handling.
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
            testPortAlreadyInUse();
            System.out.println("[PASS] Test 3: Port already in use reports BindException");
            passed++;
        } catch (Throwable t) {
            System.err.println("[FAIL] Test 3: " + t.getMessage());
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

    private static void testPortAlreadyInUse() throws IOException {
        int testPort = 5053;
        try (ChatServer server1 = new ChatServer("127.0.0.1", testPort)) {
            server1.start();

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
}
