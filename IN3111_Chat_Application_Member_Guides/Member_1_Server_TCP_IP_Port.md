# Member 1 — Server Setup, TCP, IP Addressing and Ports

## Individual responsibility
Implement the server startup and listening endpoint. Explain how the server is identified by an IP address and port, and how TCP provides a connection-oriented byte stream.

## Concepts to demonstrate
- Client-server architecture
- TCP/IP at the application-programming level
- Server IP address and port number
- `InetSocketAddress`
- `ServerSocketChannel`
- Accepting TCP connections

## Files
- `server/ChatServer.java`
- `network/NetworkConfig.java`

Coordinate with Member 3, who owns Selector registration and the NIO event loop. Member 1 owns server bootstrap/configuration; Member 3 owns readiness handling.

## Implementation tasks
1. Create a Maven Java project and package structure agreed by the group.
2. Define `HOST`/bind-address and `PORT` in `NetworkConfig`.
3. Open a `ServerSocketChannel`.
4. Bind it to an `InetSocketAddress`.
5. Configure non-blocking mode.
6. Pass the channel to Member 3's selector/event-loop component for registration and acceptance.
7. Add clear startup and shutdown messages and exception handling.
8. Document how to find the server's LAN IPv4 address for the demo. Use `127.0.0.1` only for same-machine testing; use the server computer's LAN IP for other computers.

## Acceptance tests
- Server starts and binds to the configured port.
- A client can establish a TCP connection.
- A second client can also connect.
- If the port is already in use, the server reports a useful error.
- Server shuts down cleanly and releases the port.

## Individual two-minute demonstration
1. Show the configured IP/bind address and port.
2. Start the server and explain that it listens for TCP connection requests.
3. Connect a client and show that the server accepts it.
4. Explain the difference between an IP address (identifies a network interface/host for routing) and a port (identifies the service endpoint on that host).

## Explain in the report
- Why TCP is suitable for chat (reliable, ordered byte stream).
- What the IP address and port represent.
- Why binding to `0.0.0.0` can allow LAN interfaces while clients connect to the actual LAN IP.
- The difference between localhost testing and LAN testing.

## Handover to group
Provide the server channel/bootstrap component and the configuration constants. Agree with Member 3 on a constructor or startup method before implementation.
