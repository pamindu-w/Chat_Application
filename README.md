# IN3111 Network Chat Application

This repository is the shared starting point for the five-member Java Network Programming project. It contains the agreed project structure, protocol contract, configuration, and small compiling foundations. Feature work should be added in the package assigned to each member.

## Shared decisions

- Java 21 and Maven.
- Package root: `com.networkchat`.
- TCP over `ServerSocketChannel`/`SocketChannel`.
- Server-side non-blocking NIO with one selector/event-loop owner.
- Default bind address: `0.0.0.0`; clients use the server's actual IP address.
- Default port: `5000`.
- Newline-delimited UTF-8 protocol; see [`docs/protocol.md`](docs/protocol.md).
- The server session, not client-supplied chat text, is the source of sender identity.

## Repository layout

```text
src/main/java/com/networkchat/
├── Main.java
├── client/       Member 2: client connection and console/UI integration
├── model/        Shared domain values
├── network/      Shared configuration, protocol and framing
├── server/       Members 1, 3 and 4: server, sessions and broadcasting
└── ui/           Member 5: optional Swing interface
docs/
├── architecture.md
├── protocol.md
└── testing-checklist.md
```

## Ownership and Git workflow

Use one branch per member and keep `main` buildable:

```text
member-1-server
member-2-client
member-3-nio
member-4-multithreading
member-5-protocol-ui
```

Before changing a shared class, agree on the contract in [`docs/architecture.md`](docs/architecture.md). Avoid two members rewriting the same class at the same time. Pull/rebase from `main` before opening a pull request.

## Build and run

Install JDK 21 and Maven, then run:

```bash
mvn clean test
```

The current baseline can verify its shared contracts with:

```bash
mvn -q exec:java -Dexec.mainClass=com.networkchat.Main -Dexec.args="help"
```

The full server/client commands will be documented here when Members 1 and 2 complete their entry points. Do not claim end-to-end tests have passed until the group records real results in [`docs/testing-checklist.md`](docs/testing-checklist.md).

## LAN safety

Use `127.0.0.1` only when server and client are on the same computer. For a LAN demo, find the server computer's private IPv4 address and connect to that address. Use a trusted network; do not expose this assignment server to the public internet.
