package com.networkchat.client;

import com.networkchat.model.ChatMessage;

@FunctionalInterface
public interface MessageListener {
    void onMessage(ChatMessage message);
}
