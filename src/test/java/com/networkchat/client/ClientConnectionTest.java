package com.networkchat.client;

import com.networkchat.model.ChatMessage;
import com.networkchat.network.NetworkConfig;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pure-JDK verification tests for Member 2's {@link ClientConnection} and {@link ChatClient}.
 */
public final class ClientConnectionTest {
    private ClientConnectionTest() {
    }

    public static void main(String[] args) throws Exception {
        testConnectLoginChatAndCleanLogout();
        testSplitAndMultiFrameReads();
        testUnexpectedServerShutdown();
        testConnectionRefusedAndValidation();
        testChatClientConsoleFlow();
        System.out.println("ALL_CLIENT_TESTS_PASSED");
    }

    private static void testConnectLoginChatAndCleanLogout() throws Exception {
        try (ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            int port = ((InetSocketAddress) server.getLocalAddress()).getPort();

            BlockingQueue<String> serverReceived = new LinkedBlockingQueue<>();
            CountDownLatch serverClosed = new CountDownLatch(1);
            Thread serverThread = startAcceptingSingleClient(server, serverReceived, serverClosed, client -> {
                writeUtf8(client, "JOIN|Alice\n");
                writeUtf8(client, "CHAT|Bob|Hello Alice!\n");
            });

            List<ChatMessage> clientMessages = new CopyOnWriteArrayList<>();
            CountDownLatch chatReceived = new CountDownLatch(1);
            try (ClientConnection connection = new ClientConnection(msg -> {
                clientMessages.add(msg);
                if (msg.type() == ChatMessage.Type.CHAT) {
                    chatReceived.countDown();
                }
            })) {
                connection.connect("127.0.0.1", port, "Alice");
                assertTrue(connection.isConnected(), "Client should report connected");
                assertTrue(connection.getLocalAddress() != null, "Local endpoint should be available");

                String loginLine = serverReceived.poll(3, TimeUnit.SECONDS);
                assertEquals("LOGIN|Alice", loginLine, "Expected LOGIN line");

                assertTrue(chatReceived.await(3, TimeUnit.SECONDS), "Expected CHAT message from server");
                assertEquals(2, clientMessages.size(), "Expected JOIN and CHAT messages");
                assertEquals(ChatMessage.Type.JOIN, clientMessages.get(0).type(), "First message should be JOIN");
                assertEquals("Alice", clientMessages.get(0).username(), "JOIN username");
                assertEquals("Bob", clientMessages.get(1).username(), "CHAT sender");
                assertEquals("Hello Alice!", clientMessages.get(1).body(), "CHAT body");

                connection.sendChatMessage("Hi Bob!");
                String chatLine = serverReceived.poll(3, TimeUnit.SECONDS);
                assertEquals("CHAT|Hi Bob!", chatLine, "Expected CHAT line");

                connection.disconnect();
                assertTrue(!connection.isConnected(), "Client should report disconnected after disconnect()");
                String logoutLine = serverReceived.poll(3, TimeUnit.SECONDS);
                assertEquals("LOGOUT", logoutLine, "Expected LOGOUT line on clean disconnect");
            }

            assertTrue(serverClosed.await(3, TimeUnit.SECONDS), "Server should observe EOF after disconnect");
            serverThread.join(2000);
        }
    }

    private static void testSplitAndMultiFrameReads() throws Exception {
        try (ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            int port = ((InetSocketAddress) server.getLocalAddress()).getPort();

            BlockingQueue<String> serverReceived = new LinkedBlockingQueue<>();
            CountDownLatch serverDone = new CountDownLatch(1);
            Thread serverThread = startAcceptingSingleClient(server, serverReceived, serverDone, client -> {
                writeUtf8(client, "CHAT|Bob|Part1");
                sleepQuietly(40);
                writeUtf8(client, "Part2\nJOIN|Charlie\nLEAVE|Bob\n");
            });

            List<ChatMessage> received = new CopyOnWriteArrayList<>();
            CountDownLatch allThreeReceived = new CountDownLatch(3);
            try (ClientConnection connection = new ClientConnection(msg -> {
                received.add(msg);
                allThreeReceived.countDown();
            })) {
                connection.connect("127.0.0.1", port, "Alice");
                assertEquals("LOGIN|Alice", serverReceived.poll(3, TimeUnit.SECONDS), "LOGIN line");
                assertTrue(allThreeReceived.await(3, TimeUnit.SECONDS), "All 3 framed messages should arrive");
                assertEquals("Part1Part2", received.get(0).body(), "Split TCP frame should reassemble");
                assertEquals(ChatMessage.Type.JOIN, received.get(1).type(), "Second frame JOIN");
                assertEquals(ChatMessage.Type.LEAVE, received.get(2).type(), "Third frame LEAVE");
            }
            serverDone.await(3, TimeUnit.SECONDS);
            serverThread.join(2000);
        }
    }

    private static void testUnexpectedServerShutdown() throws Exception {
        try (ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            int port = ((InetSocketAddress) server.getLocalAddress()).getPort();

            CountDownLatch loginSeen = new CountDownLatch(1);
            Thread serverThread = new Thread(() -> {
                try (SocketChannel client = server.accept()) {
                    ByteBuffer buf = ByteBuffer.allocate(256);
                    client.read(buf);
                    loginSeen.countDown();
                } catch (IOException ignored) {
                }
            }, "test-shutdown-server");
            serverThread.start();

            CountDownLatch disconnectedLatch = new CountDownLatch(1);
            AtomicReference<String> disconnectReason = new AtomicReference<>();
            try (ClientConnection connection = new ClientConnection(
                    msg -> {},
                    reason -> {
                        disconnectReason.set(reason);
                        disconnectedLatch.countDown();
                    }
            )) {
                connection.connect("127.0.0.1", port, "Alice");
                assertTrue(loginSeen.await(3, TimeUnit.SECONDS), "Server should see login before closing");
                assertTrue(disconnectedLatch.await(3, TimeUnit.SECONDS), "Disconnect listener should fire");
                assertTrue(!connection.isConnected(), "Connection should no longer be active");
                assertTrue(disconnectReason.get() != null && !disconnectReason.get().isBlank(),
                        "Disconnect reason should be provided");
            }
            serverThread.join(2000);
        }
    }

    private static void testConnectionRefusedAndValidation() throws Exception {
        int unusedPort;
        try (ServerSocketChannel temp = ServerSocketChannel.open()) {
            temp.bind(new InetSocketAddress("127.0.0.1", 0));
            unusedPort = ((InetSocketAddress) temp.getLocalAddress()).getPort();
        }

        try (ClientConnection connection = new ClientConnection(msg -> {})) {
            boolean threwIo = false;
            try {
                connection.connect("127.0.0.1", unusedPort, "Alice");
            } catch (IOException expected) {
                threwIo = true;
            }
            assertTrue(threwIo, "Connecting to closed port must throw IOException");
            assertTrue(!connection.isConnected(), "Failed connect must leave client disconnected");
        }

        try (ClientConnection connection = new ClientConnection(msg -> {})) {
            boolean rejectedBadUser = false;
            try {
                connection.connect("127.0.0.1", unusedPort, "Bad|User");
            } catch (IllegalArgumentException expected) {
                rejectedBadUser = true;
            }
            assertTrue(rejectedBadUser, "Username containing '|' must be rejected");
        }
    }

    private static void testChatClientConsoleFlow() throws Exception {
        try (ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            int port = ((InetSocketAddress) server.getLocalAddress()).getPort();

            BlockingQueue<String> serverReceived = new LinkedBlockingQueue<>();
            CountDownLatch serverDone = new CountDownLatch(1);
            Thread serverThread = startAcceptingSingleClient(server, serverReceived, serverDone, client -> {
                writeUtf8(client, "JOIN|Alice\n");
            });

            String simulatedInput = "/help\nHello console\n/quit\n";
            ByteArrayInputStream in = new ByteArrayInputStream(simulatedInput.getBytes(NetworkConfig.CHARSET));
            ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
            PrintStream out = new PrintStream(outBytes, true, NetworkConfig.CHARSET);

            ChatClient client = new ChatClient(in, out);
            client.run("127.0.0.1", port, "Alice");

            assertEquals("LOGIN|Alice", serverReceived.poll(3, TimeUnit.SECONDS), "Console LOGIN");
            assertEquals("CHAT|Hello console", serverReceived.poll(3, TimeUnit.SECONDS), "Console CHAT");
            assertEquals("LOGOUT", serverReceived.poll(3, TimeUnit.SECONDS), "Console LOGOUT");

            String consoleText = outBytes.toString(NetworkConfig.CHARSET);
            assertTrue(consoleText.contains("Connected as 'Alice'"), "Banner should show username");
            assertTrue(consoleText.contains("Disconnected cleanly."), "Exit message should be printed");

            serverDone.await(3, TimeUnit.SECONDS);
            serverThread.join(2000);
        }
    }

    @FunctionalInterface
    private interface SocketAction {
        void accept(SocketChannel client) throws IOException;
    }

    private static Thread startAcceptingSingleClient(
            ServerSocketChannel server,
            BlockingQueue<String> receivedLines,
            CountDownLatch eofLatch,
            SocketAction onFirstLine
    ) {
        Thread thread = new Thread(() -> {
            try (SocketChannel client = server.accept()) {
                StringBuilder pending = new StringBuilder();
                ByteBuffer buf = ByteBuffer.allocate(1024);
                boolean firstActionDone = false;
                while (true) {
                    buf.clear();
                    int n = client.read(buf);
                    if (n == -1) {
                        break;
                    }
                    buf.flip();
                    pending.append(NetworkConfig.CHARSET.decode(buf));
                    int idx;
                    while ((idx = pending.indexOf("\n")) >= 0) {
                        String line = pending.substring(0, idx);
                        pending.delete(0, idx + 1);
                        receivedLines.add(line);
                        if (!firstActionDone) {
                            firstActionDone = true;
                            onFirstLine.accept(client);
                        }
                    }
                }
            } catch (IOException ignored) {
            } finally {
                eofLatch.countDown();
            }
        }, "test-mock-server");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void writeUtf8(SocketChannel channel, String text) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(text.getBytes(NetworkConfig.CHARSET));
        while (buf.hasRemaining()) {
            channel.write(buf);
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }
}
