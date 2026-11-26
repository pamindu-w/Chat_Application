package com.networkchat.client;

import com.networkchat.model.ChatMessage;
import com.networkchat.network.MessageProtocol;
import com.networkchat.network.NetworkConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Console entry point and interactive lifecycle wrapper for {@link ClientConnection}.
 */
public final class ChatClient {
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final BufferedReader in;
    private final PrintStream out;
    private final Object outputLock = new Object();

    public ChatClient(InputStream in, PrintStream out) {
        this.in = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(in, "in must not be null"),
                NetworkConfig.CHARSET
        ));
        this.out = Objects.requireNonNull(out, "out must not be null");
    }

    public static void main(String[] args) {
        if (args.length == 1 && ("--help".equalsIgnoreCase(args[0]) || "-h".equalsIgnoreCase(args[0]))) {
            printUsage(System.out);
            return;
        }
        if (args.length > 3) {
            System.err.println("Too many arguments.");
            printUsage(System.err);
            return;
        }

        ChatClient client = new ChatClient(System.in, System.out);
        try {
            ConnectionParams params = client.resolveConnectionParams(args);
            if (params == null) {
                return;
            }
            client.run(params.host(), params.port(), params.username());
        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }

    /** Connects to the server, prints incoming events, and sends console chat lines until exit. */
    public void run(String host, int port, String username) throws IOException {
        AtomicBoolean active = new AtomicBoolean(true);

        try (ClientConnection connection = new ClientConnection(
                this::displayMessage,
                reason -> {
                    active.set(false);
                    println("[DISCONNECTED] " + reason);
                }
        )) {
            connection.connect(host, port, username);
            printConnectionBanner(connection);

            while (active.get() && connection.isConnected()) {
                String line = in.readLine();
                if (line == null) {
                    break;
                }
                String trimmed = line.trim();
                if ("/quit".equalsIgnoreCase(trimmed) || "/exit".equalsIgnoreCase(trimmed)) {
                    break;
                }
                if ("/help".equalsIgnoreCase(trimmed)) {
                    printHelp();
                    continue;
                }
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (!connection.isConnected()) {
                    println("[INFO] Not connected to server.");
                    break;
                }
                try {
                    connection.sendChatMessage(line);
                } catch (IllegalArgumentException validationError) {
                    println("[INVALID] " + validationError.getMessage()
                            + " (1-" + NetworkConfig.MAX_MESSAGE_LENGTH + " chars, no '|' or newlines)");
                } catch (IllegalStateException disconnectedError) {
                    println("[DISCONNECTED] " + disconnectedError.getMessage());
                    break;
                }
            }
        }
        println("Disconnected cleanly.");
    }

    private ConnectionParams resolveConnectionParams(String[] args) throws IOException {
        String host = args.length >= 1 ? args[0].trim() : promptHost();
        if (host == null) {
            return null;
        }

        Integer port = args.length >= 2 ? parsePort(args[1]) : promptPort();
        if (port == null) {
            return null;
        }

        String username = args.length >= 3 ? validateUsernameOrNull(args[2]) : promptUsername();
        if (username == null) {
            return null;
        }
        return new ConnectionParams(host, port, username);
    }

    private String promptHost() throws IOException {
        out.print("Server IP/host [" + NetworkConfig.DEFAULT_HOST + "]: ");
        out.flush();
        String input = in.readLine();
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.isEmpty() ? NetworkConfig.DEFAULT_HOST : trimmed;
    }

    private Integer promptPort() throws IOException {
        while (true) {
            out.print("Server port [" + NetworkConfig.DEFAULT_PORT + "]: ");
            out.flush();
            String input = in.readLine();
            if (input == null) {
                return null;
            }
            String trimmed = input.trim();
            if (trimmed.isEmpty()) {
                return NetworkConfig.DEFAULT_PORT;
            }
            try {
                return parsePort(trimmed);
            } catch (IllegalArgumentException e) {
                out.println(e.getMessage());
            }
        }
    }

    private String promptUsername() throws IOException {
        while (true) {
            out.print("Username (1-" + NetworkConfig.MAX_USERNAME_LENGTH + " chars, no '|'): ");
            out.flush();
            String input = in.readLine();
            if (input == null) {
                return null;
            }
            String trimmed = input.trim();
            try {
                MessageProtocol.login(trimmed);
                return trimmed;
            } catch (IllegalArgumentException e) {
                out.println("Invalid username. Must be 1-" + NetworkConfig.MAX_USERNAME_LENGTH
                        + " characters without '|', '\\r', or '\\n'.");
            }
        }
    }

    private static int parsePort(String raw) {
        try {
            int port = Integer.parseInt(raw.trim());
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535.");
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port number: " + raw);
        }
    }

    private static String validateUsernameOrNull(String raw) {
        String trimmed = raw.trim();
        MessageProtocol.login(trimmed);
        return trimmed;
    }

    private void displayMessage(ChatMessage message) {
        String time = TIME_FORMATTER.format(message.timestamp());
        String formatted = switch (message.type()) {
            case JOIN -> "[" + time + "] * " + message.username() + " joined the chat";
            case LEAVE -> "[" + time + "] * " + message.username() + " left the chat";
            case CHAT -> "[" + time + "] " + message.username() + ": " + message.body();
            case ERROR -> "[" + time + "] [ERROR] " + message.body();
        };
        println(formatted);
    }

    private void printConnectionBanner(ClientConnection connection) {
        InetSocketAddress local = connection.getLocalAddress();
        InetSocketAddress remote = connection.getRemoteAddress();
        println("Connected as '" + connection.getUsername() + "'");
        println("  Local endpoint : " + (local != null ? local : "unknown"));
        println("  Server endpoint: " + remote);
        println("Type a message and press Enter to send. Commands: /help, /quit");
    }

    private void printHelp() {
        println("Commands:");
        println("  /help  Show available commands");
        println("  /quit  Send LOGOUT and disconnect cleanly");
        println("  /exit  Same as /quit");
    }

    private void println(String line) {
        synchronized (outputLock) {
            out.println(line);
            out.flush();
        }
    }

    private static void printUsage(PrintStream stream) {
        stream.println("Usage: ChatClient [host] [port] [username]");
        stream.println("Defaults: host=" + NetworkConfig.DEFAULT_HOST + ", port=" + NetworkConfig.DEFAULT_PORT);
    }

    private record ConnectionParams(String host, int port, String username) {
    }
}
