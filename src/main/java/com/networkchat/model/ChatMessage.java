package com.networkchat.model;

import java.time.Instant;

/** Immutable, parsed chat event shared by the server, client and optional UI. */
public record ChatMessage(Type type, String username, String body, Instant timestamp) {
    public enum Type { JOIN, CHAT, LEAVE, ERROR }
}
