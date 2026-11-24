package com.networkchat.network;

import com.networkchat.model.ChatMessage;

import java.time.Instant;

/** Protocol encoding/parsing boundary. Fields containing delimiters are rejected in v1. */
public final class MessageProtocol {
    private MessageProtocol() {
    }

    public static String login(String username) {
        return "LOGIN|" + validField(username, "username", NetworkConfig.MAX_USERNAME_LENGTH) + "\n";
    }

    public static String chat(String message) {
        return "CHAT|" + validField(message, "message", NetworkConfig.MAX_MESSAGE_LENGTH) + "\n";
    }

    public static String logout() {
        return "LOGOUT\n";
    }

    public static ChatMessage parseServerLine(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("Message cannot be empty");
        }
        String[] fields = line.split("\\|", -1);
        return switch (fields[0]) {
            case "JOIN", "LEAVE" -> {
                requireFields(fields, 2);
                yield new ChatMessage(ChatMessage.Type.valueOf(fields[0]), fields[1], "", Instant.now());
            }
            case "CHAT" -> {
                requireFields(fields, 3);
                yield new ChatMessage(ChatMessage.Type.CHAT, fields[1], fields[2], Instant.now());
            }
            case "ERROR" -> {
                requireFields(fields, 2);
                yield new ChatMessage(ChatMessage.Type.ERROR, "", fields[1], Instant.now());
            }
            default -> throw new IllegalArgumentException("Unknown server message type: " + fields[0]);
        };
    }

    private static String validField(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength
                || value.indexOf('|') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        return value;
    }

    private static void requireFields(String[] fields, int expected) {
        if (fields.length != expected) {
            throw new IllegalArgumentException("Expected " + expected + " protocol fields");
        }
    }
}
