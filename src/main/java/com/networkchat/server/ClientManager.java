package com.networkchat.server;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Selector-thread-owned registry for authenticated client sessions. */
public final class ClientManager {
    private final Map<String, ClientSession> sessionsByUsername = new LinkedHashMap<>();

    public boolean add(String username, ClientSession session) {
        if (sessionsByUsername.containsKey(username)) {
            return false;
        }
        sessionsByUsername.put(username, session);
        return true;
    }

    public ClientSession remove(String username) {
        return sessionsByUsername.remove(username);
    }

    public ClientSession find(String username) {
        return sessionsByUsername.get(username);
    }

    public Collection<ClientSession> sessions() {
        return sessionsByUsername.values();
    }

    public int size() {
        return sessionsByUsername.size();
    }
}
