# Shared Architecture Contract

This document is the integration boundary for the five members. It is intentionally small: feature implementations belong in the assigned packages, while these rules keep the branches compatible.

## Component responsibilities

| Component | Owner | Contract |
|---|---|---|
| `NetworkConfig` | Shared | Port, buffer size, limits and charset |
| `MessageProtocol` | Member 5/shared | Encode and parse protocol lines |
| `LineFramer` | Member 3/shared | Reconstruct newline-delimited frames from TCP bytes |
| `ClientSession` | Member 3 | Per-connection buffers, queue and authentication state |
| `ChatServer` | Members 1 + 3 | Bind/lifecycle plus selector event loop |
| `ClientManager` | Member 4 | Authoritative username/session registry |
| `MessageBroadcaster` | Member 4 | Broadcast encoded messages through session queues |
| `ClientConnection` | Member 2 | Client connect, send, receive and disconnect |
| `MessageListener` | Members 2 + 5 | Deliver incoming messages to console/UI |

## Thread ownership

The selector thread owns channel reads/writes, selection-key registration and interest-operation changes. Worker threads may perform bounded background validation/logging, but communicate network changes through a thread-safe command queue and `Selector.wakeup()`.

## Integration order

1. Member 1 binds the server channel.
2. Member 2 connects one client.
3. Member 3 adds accept/read/write readiness handling.
4. Member 5 finalizes protocol parsing and framing tests.
5. Member 4 adds authenticated sessions, broadcasting and bounded worker work.
6. The group runs two-client, three-client, failure, split-frame, multi-frame and LAN tests.
7. Member 5 adds Swing only after console networking is stable.
