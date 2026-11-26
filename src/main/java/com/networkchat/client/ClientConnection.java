package com.networkchat.client;

import com.networkchat.model.ChatMessage;
import com.networkchat.network.LineFramer;
import com.networkchat.network.MessageProtocol;
import com.networkchat.network.NetworkConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Client-side TCP connection built on Java NIO {@link SocketChannel} and {@link Selector}.
 *
 * <p>A single I/O thread owns the selector, channel reads/writes, and frame decoding so the
 * console or Swing UI thread never blocks on network operations and partial writes are drained
 * safely through an outgoing buffer queue.
 */
public final class ClientConnection implements AutoCloseable {
    private static final long CONNECT_TIMEOUT_SECONDS = 10;
    private static final long DISCONNECT_FLUSH_TIMEOUT_MS = 500;

    private final MessageListener messageListener;
    private final Consumer<String> disconnectListener;
    private final boolean notifyDisconnectViaMessageListener;

    private final Queue<ByteBuffer> outgoing = new ConcurrentLinkedQueue<>();
    private final ByteBuffer inputBuffer = ByteBuffer.allocate(NetworkConfig.BUFFER_SIZE);
    private final LineFramer framer = new LineFramer();

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean disconnectRequested = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean disconnectNotified = new AtomicBoolean(false);
    private final CountDownLatch ioFinishedLatch = new CountDownLatch(1);

    private volatile Selector selector;
    private volatile SocketChannel channel;
    private volatile Thread ioThread;
    private volatile String username;
    private volatile InetSocketAddress remoteAddress;

    public ClientConnection(MessageListener messageListener) {
        this.messageListener = Objects.requireNonNull(messageListener, "messageListener must not be null");
        this.disconnectListener = null;
        this.notifyDisconnectViaMessageListener = true;
    }

    public ClientConnection(MessageListener messageListener, Consumer<String> disconnectListener) {
        this.messageListener = Objects.requireNonNull(messageListener, "messageListener must not be null");
        this.disconnectListener = Objects.requireNonNull(disconnectListener, "disconnectListener must not be null");
        this.notifyDisconnectViaMessageListener = false;
    }

    /**
     * Connects to the server endpoint, negotiates non-blocking TCP connection completion via
     * {@code OP_CONNECT}, and sends the initial {@code LOGIN|username} protocol line.
     */
    public void connect(String host, int port, String username) throws IOException {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Server host cannot be empty");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        String loginLine = MessageProtocol.login(username);

        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("ClientConnection cannot be reused after connect");
        }

        InetSocketAddress endpoint = new InetSocketAddress(host.trim(), port);
        if (endpoint.isUnresolved()) {
            closed.set(true);
            ioFinishedLatch.countDown();
            throw new UnknownHostException("Unable to resolve server host: " + host);
        }

        this.username = username;
        this.remoteAddress = endpoint;

        Selector openedSelector = null;
        SocketChannel openedChannel = null;
        CompletableFuture<Void> connectFuture = new CompletableFuture<>();

        try {
            openedSelector = Selector.open();
            openedChannel = SocketChannel.open();
            openedChannel.configureBlocking(false);

            this.selector = openedSelector;
            this.channel = openedChannel;

            boolean connectedImmediately = openedChannel.connect(endpoint);
            if (connectedImmediately) {
                openedChannel.register(openedSelector, SelectionKey.OP_READ);
                completeConnection(loginLine, connectFuture);
            } else {
                openedChannel.register(openedSelector, SelectionKey.OP_CONNECT);
            }

            Thread thread = new Thread(
                    () -> runIoLoop(loginLine, connectFuture),
                    "client-nio-io"
            );
            thread.setDaemon(true);
            this.ioThread = thread;
            thread.start();

            awaitConnect(connectFuture);
        } catch (IOException | RuntimeException e) {
            forceCloseResources();
            ioFinishedLatch.countDown();
            throw e;
        }
    }

    /** Validates and queues a chat message for transmission using the shared protocol. */
    public void sendChatMessage(String message) {
        sendProtocolLine(MessageProtocol.chat(message));
    }

    /** Queues a raw protocol line (appending {@code \n} if omitted) for non-blocking writing. */
    public void sendProtocolLine(String protocolLine) {
        if (protocolLine == null || protocolLine.isBlank()) {
            throw new IllegalArgumentException("Protocol line cannot be empty");
        }
        if (!isConnected()) {
            throw new IllegalStateException("Client is not connected");
        }
        String framed = protocolLine.endsWith("\n") ? protocolLine : protocolLine + "\n";
        enqueueRaw(framed);
        Selector activeSelector = this.selector;
        if (activeSelector != null) {
            activeSelector.wakeup();
        }
    }

    /** Sends {@code LOGOUT} if connected and cleanly closes the channel and selector. */
    public void disconnect() {
        if (closed.get()) {
            return;
        }
        boolean firstRequest = disconnectRequested.compareAndSet(false, true);
        if (firstRequest && connected.get()) {
            enqueueRaw(MessageProtocol.logout());
        }

        Selector activeSelector = this.selector;
        if (activeSelector != null) {
            activeSelector.wakeup();
        }

        if (Thread.currentThread() != ioThread && started.get()) {
            try {
                ioFinishedLatch.await(DISCONNECT_FLUSH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        forceCloseResources();
    }

    @Override
    public void close() {
        disconnect();
    }

    public boolean isConnected() {
        return connected.get() && !disconnectRequested.get() && !closed.get();
    }

    public String getUsername() {
        return username;
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public InetSocketAddress getLocalAddress() {
        SocketChannel activeChannel = this.channel;
        if (activeChannel == null) {
            return null;
        }
        try {
            return (InetSocketAddress) activeChannel.getLocalAddress();
        } catch (IOException e) {
            return null;
        }
    }

    private void awaitConnect(CompletableFuture<Void> connectFuture) throws IOException {
        try {
            connectFuture.get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while connecting to " + remoteAddress, e);
        } catch (TimeoutException e) {
            throw new IOException("Timed out connecting to " + remoteAddress, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Failed to connect to " + remoteAddress, cause);
        }
    }

    private void runIoLoop(String loginLine, CompletableFuture<Void> connectFuture) {
        String unexpectedReason = null;
        try {
            Selector activeSelector = this.selector;
            SocketChannel activeChannel = this.channel;

            while (!closed.get() && activeSelector.isOpen()) {
                SelectionKey channelKey = activeChannel.keyFor(activeSelector);
                if (channelKey != null && channelKey.isValid() && connected.get()) {
                    int desiredOps = SelectionKey.OP_READ
                            | (outgoing.isEmpty() ? 0 : SelectionKey.OP_WRITE);
                    if (channelKey.interestOps() != desiredOps) {
                        channelKey.interestOps(desiredOps);
                    }
                }

                if (disconnectRequested.get() && outgoing.isEmpty()) {
                    break;
                }

                activeSelector.select();
                if (closed.get()) {
                    break;
                }

                Iterator<SelectionKey> keys = activeSelector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    if (!key.isValid()) {
                        continue;
                    }
                    if (key.isConnectable()) {
                        handleConnect(key, loginLine, connectFuture);
                    }
                    if (key.isValid() && key.isReadable()) {
                        handleRead();
                    }
                    if (key.isValid() && key.isWritable()) {
                        handleWrite(key);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            if (!connectFuture.isDone()) {
                connectFuture.completeExceptionally(e);
            } else if (!disconnectRequested.get() && !closed.get()) {
                unexpectedReason = e.getMessage() != null && !e.getMessage().isBlank()
                        ? e.getMessage()
                        : e.getClass().getSimpleName();
            }
        } finally {
            forceCloseResources();
            ioFinishedLatch.countDown();
            if (unexpectedReason != null) {
                notifyUnexpectedDisconnect(unexpectedReason);
            }
        }
    }

    private void handleConnect(SelectionKey key, String loginLine, CompletableFuture<Void> connectFuture)
            throws IOException {
        SocketChannel activeChannel = (SocketChannel) key.channel();
        if (activeChannel.finishConnect()) {
            key.interestOps(SelectionKey.OP_READ);
            completeConnection(loginLine, connectFuture);
        }
    }

    private void completeConnection(String loginLine, CompletableFuture<Void> connectFuture) {
        enqueueRaw(loginLine);
        connected.set(true);
        connectFuture.complete(null);
    }

    private void handleRead() throws IOException {
        inputBuffer.clear();
        int bytesRead = channel.read(inputBuffer);
        if (bytesRead == -1) {
            throw new IOException("Server closed the connection");
        }
        if (bytesRead == 0) {
            return;
        }

        inputBuffer.flip();
        List<String> lines = framer.accept(inputBuffer);
        for (String line : lines) {
            try {
                ChatMessage message = MessageProtocol.parseServerLine(line);
                messageListener.onMessage(message);
            } catch (IllegalArgumentException parseError) {
                messageListener.onMessage(new ChatMessage(
                        ChatMessage.Type.ERROR,
                        "",
                        "Malformed server message: " + parseError.getMessage(),
                        Instant.now()
                ));
            }
        }
    }

    private void handleWrite(SelectionKey key) throws IOException {
        while (!outgoing.isEmpty()) {
            ByteBuffer buffer = outgoing.peek();
            channel.write(buffer);
            if (buffer.hasRemaining()) {
                // Socket send buffer is full; keep head buffer queued for the next OP_WRITE.
                return;
            }
            outgoing.poll();
        }
        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
    }

    private void enqueueRaw(String protocolLine) {
        outgoing.add(ByteBuffer.wrap(protocolLine.getBytes(NetworkConfig.CHARSET)));
    }

    private void notifyUnexpectedDisconnect(String reason) {
        if (!disconnectNotified.compareAndSet(false, true)) {
            return;
        }
        if (disconnectListener != null) {
            disconnectListener.accept(reason);
        } else if (notifyDisconnectViaMessageListener) {
            messageListener.onMessage(new ChatMessage(
                    ChatMessage.Type.ERROR,
                    "",
                    reason,
                    Instant.now()
            ));
        }
    }

    private void forceCloseResources() {
        connected.set(false);
        closed.set(true);

        SocketChannel activeChannel = this.channel;
        if (activeChannel != null) {
            try {
                activeChannel.close();
            } catch (IOException ignored) {
            }
        }

        Selector activeSelector = this.selector;
        if (activeSelector != null) {
            try {
                activeSelector.close();
            } catch (IOException ignored) {
            }
        }
    }
}
