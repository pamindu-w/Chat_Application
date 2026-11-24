# Member 2 — Client Connection and SocketChannel

## Individual responsibility
Implement the client-side TCP connection and the API used by the user interface or console to send/receive chat data.

## Concepts to demonstrate
- Client-server architecture
- TCP connection establishment
- Client endpoint and server endpoint
- `SocketChannel`
- `InetSocketAddress`
- Reading and writing through a channel

## Files
- `client/ChatClient.java`
- `client/ClientConnection.java` (or an agreed equivalent)

## Implementation tasks
1. Accept server host/IP, port, and username as configuration/input.
2. Open a `SocketChannel`.
3. Connect to the server's `InetSocketAddress`.
4. Configure the channel as non-blocking if the client design uses a selector; otherwise document the agreed client-side I/O strategy.
5. Send `LOGIN|username\n` after connection.
6. Provide a method to send a protocol line to the server.
7. Provide a clean disconnect method that sends `LOGOUT|username\n` where possible and closes the channel.
8. Coordinate with Member 4/5 on how incoming messages are delivered to the UI.

## Important NIO details
- `SocketChannel.write()` may write only part of a buffer. Keep the unwritten remainder and retry when the channel is writable.
- Do not assume a single read equals one complete message.
- If using a background reader, do not block the Swing event-dispatch thread.
- Avoid concurrent writes to the same channel without synchronization or a single-writer queue.

## Acceptance tests
- Client connects to a server on localhost.
- Client connects to a server on another computer using its LAN IP.
- Client sends a login line and a chat line.
- Client handles server shutdown/disconnection without crashing.
- Client closes resources on exit.

## Individual two-minute demonstration
1. Start the server.
2. Run the client and enter the server IP/port.
3. Show successful connection.
4. Send a message and explain how `SocketChannel` represents the TCP connection.

## Explain in the report
- What a socket endpoint is.
- How a TCP connection is established at a high level.
- Why the client needs the server IP and listening port.
- How NIO channels differ from traditional blocking `Socket` usage.
