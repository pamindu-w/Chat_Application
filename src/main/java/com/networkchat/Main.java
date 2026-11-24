package com.networkchat;

import com.networkchat.network.NetworkConfig;

/** Shared entry point placeholder until the server/client entry points are integrated. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        if (args.length == 1 && "help".equalsIgnoreCase(args[0])) {
            System.out.println("Network Chat Application");
            System.out.println("Default endpoint: " + NetworkConfig.DEFAULT_BIND_ADDRESS + ":" + NetworkConfig.DEFAULT_PORT);
            System.out.println("Server and client commands will be added by Members 1 and 2.");
            return;
        }
        System.out.println("Usage: java -jar network-chat-application.jar help");
    }
}
