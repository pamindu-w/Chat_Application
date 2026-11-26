package com.networkchat.network;

import com.networkchat.server.ClientManager;
import com.networkchat.server.ClientSession;
import com.networkchat.server.MessageBroadcaster;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Main Java NIO server manager.
 *
 * Uses Selector, ServerSocketChannel and SocketChannel
 * to handle multiple clients using non-blocking I/O.
 *
 * All channel operations and SelectionKey updates are
 * performed by the selector thread.
 */
public final class NIOManager implements Runnable, AutoCloseable {

    private final String bindAddress;
    private final int port;

    private Selector selector;
    private ServerSocketChannel serverChannel;

    private volatile boolean running;

    private final ClientManager clientManager;
    private final MessageBroadcaster broadcaster;

    // All connected sessions, including clients not yet authenticated.
    private final Set<ClientSession> allSessions = new HashSet<>();

    // Stores incomplete message lines between socket reads.
    private final Map<ClientSession, ByteArrayOutputStream> partialLines =
            new HashMap<>();

    private static final int MAX_LINE_BYTES = 2048;

    public NIOManager() {
        this(
                NetworkConfig.DEFAULT_BIND_ADDRESS,
                NetworkConfig.DEFAULT_PORT
        );
    }

    public NIOManager(String bindAddress, int port) {
        this.bindAddress = bindAddress;
        this.port = port;

        this.clientManager = new ClientManager();
        this.broadcaster = new MessageBroadcaster();
    }

    /**
     * Starts the NIO server.
     */
    @Override
    public void run() {

        try {
            selector = Selector.open();

            serverChannel = ServerSocketChannel.open();

            serverChannel.configureBlocking(false);

            serverChannel.bind(
                    new InetSocketAddress(bindAddress, port)
            );

            serverChannel.register(
                    selector,
                    SelectionKey.OP_ACCEPT
            );

            running = true;

            System.out.println(
                    "Chat server started on "
                            + bindAddress + ":" + port
            );

            // Main NIO event loop.
            while (running) {

                selector.select();

                Iterator<SelectionKey> iterator =
                        selector.selectedKeys().iterator();

                while (iterator.hasNext()) {

                    SelectionKey key = iterator.next();
                    iterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    try {

                        if (key.isAcceptable()) {
                            acceptClient();
                        }

                        if (key.isValid() && key.isReadable()) {
                            readClient(key);
                        }

                        if (key.isValid() && key.isWritable()) {
                            writeClient(key);
                        }

                    } catch (IOException | RuntimeException e) {

                        Object attachment = key.attachment();

                        if (attachment instanceof ClientSession session) {
                            disconnectClient(session);
                        } else {
                            System.err.println(
                                    "Server channel error: " + e.getMessage()
                            );
                        }
                    }
                }
            }

        } catch (IOException e) {

            System.err.println(
                    "NIO server error: " + e.getMessage()
            );

        } finally {
            running = false;
            shutdownResources();
        }
    }

    /**
     * Accepts a new client connection.
     */
    private void acceptClient() throws IOException {

        SocketChannel clientChannel = serverChannel.accept();

        if (clientChannel == null) {
            return;
        }

        clientChannel.configureBlocking(false);

        ClientSession session = new ClientSession(
                clientChannel,
                NetworkConfig.BUFFER_SIZE
        );

        SelectionKey key = clientChannel.register(
                selector,
                SelectionKey.OP_READ,
                session
        );

        session.key(key);

        allSessions.add(session);

        partialLines.put(
                session,
                new ByteArrayOutputStream()
        );

        System.out.println(
                "New client connected: "
                        + clientChannel.getRemoteAddress()
        );
    }

    /**
     * Reads incoming data from a client.
     */
    private void readClient(SelectionKey key) throws IOException {

        ClientSession session =
                (ClientSession) key.attachment();

        SocketChannel channel = session.channel();

        ByteBuffer buffer = session.inputBuffer();

        int bytesRead = channel.read(buffer);

        if (bytesRead == -1) {
            disconnectClient(session);
            return;
        }

        if (bytesRead == 0) {
            return;
        }

        buffer.flip();

        ByteArrayOutputStream lineBuffer =
                partialLines.get(session);

        while (buffer.hasRemaining()) {

            byte currentByte = buffer.get();

            if (currentByte == '\n') {

                byte[] lineBytes = lineBuffer.toByteArray();

                lineBuffer.reset();

                String line = new String(
                        lineBytes,
                        StandardCharsets.UTF_8
                );

                // Remove optional carriage return.
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }

                processClientMessage(session, line);

                if (!channel.isOpen()) {
                    return;
                }

            } else {

                if (lineBuffer.size() >= MAX_LINE_BYTES) {
                    System.out.println("Client sent an oversized message.");
                    disconnectClient(session);
                    return;
                }

                lineBuffer.write(currentByte);
            }
        }

        buffer.clear();
    }

    /**
     * Processes a complete protocol message.
     */
    private void processClientMessage(
            ClientSession session,
            String line) {

        if (line == null || line.isBlank()) {
            return;
        }

        // LOGIN message.
        if (line.startsWith("LOGIN|")) {

            handleLogin(session, line.substring(6));
            return;
        }

        // A client must authenticate before chatting.
        if (!session.authenticated()) {
            queueDirectMessage(
                    session,
                    "ERROR|Please login first\n"
            );
            return;
        }

        // CHAT message.
        if (line.startsWith("CHAT|")) {

            handleChat(session, line.substring(5));
            return;
        }

        // LOGOUT message.
        if (line.equals("LOGOUT")) {
            disconnectClient(session);
            return;
        }

        queueDirectMessage(
                session,
                "ERROR|Unknown message type\n"
        );
    }

    /**
     * Authenticates and registers a client.
     */
    private void handleLogin(
            ClientSession session,
            String username) {

        if (session.authenticated()) {
            queueDirectMessage(
                    session,
                    "ERROR|Already logged in\n"
            );
            return;
        }

        if (username == null
                || username.isBlank()
                || username.length() > NetworkConfig.MAX_USERNAME_LENGTH
                || username.contains("|")) {

            queueDirectMessage(
                    session,
                    "ERROR|Invalid username\n"
            );
            return;
        }

        boolean added = clientManager.add(username, session);

        if (!added) {
            queueDirectMessage(
                    session,
                    "ERROR|Username already taken\n"
            );
            return;
        }

        session.username(username);

        System.out.println(
                username + " successfully logged in."
        );

        // Notify other authenticated clients.
        broadcaster.queueForOthers(
                clientManager,
                session,
                "JOIN|" + username + "\n"
        );
    }

    /**
     * Handles a chat message from an authenticated client.
     */
    private void handleChat(
            ClientSession sender,
            String message) {

        if (message == null
                || message.isBlank()
                || message.length() > NetworkConfig.MAX_MESSAGE_LENGTH
                || message.contains("\n")
                || message.contains("\r")
                || message.contains("|")) {

            queueDirectMessage(
                    sender,
                    "ERROR|Invalid chat message\n"
            );
            return;
        }

        String username = sender.username();

        String protocolLine =
                "CHAT|" + username + "|" + message + "\n";

        // Send the message to everyone except its sender.
        broadcaster.queueForOthers(
                clientManager,
                sender,
                protocolLine
        );

        System.out.println(
                username + ": " + message
        );
    }

    /**
     * Adds a direct message to one client's outgoing queue.
     */
    private void queueDirectMessage(
            ClientSession session,
            String message) {

        if (!session.channel().isOpen()) {
            return;
        }

        ByteBuffer buffer = ByteBuffer.wrap(
                message.getBytes(StandardCharsets.UTF_8)
        );

        session.outgoing().add(buffer);

        SelectionKey key = session.key();

        if (key != null && key.isValid()) {
            key.interestOps(
                    key.interestOps() | SelectionKey.OP_WRITE
            );
        }
    }

    /**
     * Writes queued messages to a client.
     */
    private void writeClient(SelectionKey key) throws IOException {

        ClientSession session =
                (ClientSession) key.attachment();

        SocketChannel channel = session.channel();

        while (!session.outgoing().isEmpty()) {

            ByteBuffer buffer = session.outgoing().peek();

            channel.write(buffer);

            // Partial write: wait for the next OP_WRITE event.
            if (buffer.hasRemaining()) {
                return;
            }

            session.outgoing().poll();
        }

        // No pending messages: disable OP_WRITE.
        key.interestOps(
                key.interestOps() & ~SelectionKey.OP_WRITE
        );
    }

    /**
     * Disconnects and removes a client.
     */
    private void disconnectClient(ClientSession session) {

        if (!allSessions.remove(session)) {
            return;
        }

        String username = session.username();

        if (username != null) {

            clientManager.remove(username);

            broadcaster.queueForOthers(
                    clientManager,
                    session,
                    "LEAVE|" + username + "\n"
            );

            System.out.println(
                    username + " disconnected."
            );
        }

        partialLines.remove(session);

        SelectionKey key = session.key();

        if (key != null) {
            key.cancel();
        }

        try {
            session.channel().close();
        } catch (IOException e) {
            System.err.println(
                    "Error closing client: " + e.getMessage()
            );
        }

        session.outgoing().clear();
    }

    /**
     * Returns the number of authenticated clients.
     */
    public int getConnectedClientCount() {
        return clientManager.size();
    }

    /**
     * Stops the server.
     */
    @Override
    public void close() {

        running = false;

        if (selector != null) {
            selector.wakeup();
        }
    }

    /**
     * Releases server resources.
     */
    private void shutdownResources() {

        for (ClientSession session :
                new HashSet<>(allSessions)) {

            disconnectClient(session);
        }

        try {
            if (serverChannel != null) {
                serverChannel.close();
            }

            if (selector != null) {
                selector.close();
            }

        } catch (IOException e) {
            System.err.println(
                    "Error shutting down server: " + e.getMessage()
            );
        }

        System.out.println("Chat server stopped.");
    }
}