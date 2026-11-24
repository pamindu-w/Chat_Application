# IN3111 Network Programming — Complete Java NIO Chat Application Guide

**Assignment:** Assignment 02  
**Submission deadline:** 26 November 2026  
**Project:** TCP Client–Server Chat Application using Java NIO and Multithreading  
**Group assumption:** Five members (adapt the allocation if your group has a different number)

---

# 1. Project overview

## 1.1 What you are building

Build a Java chat application in which multiple users connect to one central server and exchange messages in real time.

The application has two main programs:

1. **Chat server** — listens for incoming TCP connections, keeps track of connected users, receives messages, and distributes messages to the appropriate clients.
2. **Chat client** — connects to the server, lets a user identify themselves with a username, sends messages, and displays messages received from the server.

A simple graphical user interface can be added using Java Swing. However, first build and test the networking application using the console. The network communication is the main requirement; a polished interface should come later.

## 1.2 Minimum features

The first complete version should support:

- Starting and stopping the server.
- Connecting several clients to the server.
- Entering a username when connecting.
- Sending a message from one client.
- Broadcasting that message to the other connected clients.
- Displaying join and leave notifications.
- Disconnecting one client without stopping the server or affecting other clients.
- Handling invalid input and unexpected disconnections.
- Demonstrating Java NIO and multithreading in the actual implementation.

## 1.3 What the assignment requires

The assignment instructions require:

- An application of your choice developed using Java Network Programming (`java.net` package).
- Use of Java NIO (New I/O) concepts.
- Each group member to individually implement and demonstrate at least one network programming concept discussed in lectures.
- Complete source code and a project report in PDF format.
- Both the project and report inside one ZIP file.
- One group submission through Moodle.
- A physical demonstration, with a maximum of two minutes per member and ten minutes per group.

**Important interpretation:** Java NIO classes are primarily in `java.nio` and `java.nio.channels`, while network address classes such as `InetSocketAddress` are in `java.net`. Use both packages appropriately. Do not use only traditional blocking `Socket`/`ServerSocket` and claim that this demonstrates NIO.

---

# 2. Technologies and concepts

| Technology / concept | Java API or class | How the project uses it |
|---|---|---|
| Java | Java language | Main programming language |
| IP address and port | `InetAddress`, `InetSocketAddress` | Identify the server endpoint |
| TCP | `ServerSocketChannel`, `SocketChannel` | Reliable, ordered connection-oriented communication |
| Server socket | `ServerSocketChannel` | Listen for client connections |
| Client socket | `SocketChannel` | Represent a TCP connection |
| Java NIO | `Selector`, `SelectionKey`, channels and buffers | Monitor many connections without blocking on each one |
| Buffering | `ByteBuffer` | Read and write network bytes |
| Non-blocking I/O | `configureBlocking(false)` | Avoid waiting on one client while other clients need service |
| Multithreading | `ExecutorService` | Run selected background tasks |
| Input/output | `ReadableByteChannel`, `WritableByteChannel`, channel read/write | Transfer data |
| User interface (optional) | Java Swing | Login and chat windows |
| Message protocol | Project-defined protocol | Define how login, chat and logout messages are represented |

## 2.1 IP address

An IP address identifies a network interface so that packets can be routed to the correct host.

Examples:

- `127.0.0.1` — loopback address; use it when the server and client run on the same computer.
- `192.168.1.10` — an example private LAN address; use the actual server computer's LAN address when clients run on other computers on the same Wi-Fi/LAN.

Do not assume that the example address will be the address assigned to your computer.

## 2.2 Port number

A port identifies the application/service endpoint on a host.

For this project, use port `5000` as the agreed development default. The server listens on that port, and clients connect to the server's IP address and port.

An IP address identifies the host/interface; the port identifies the service endpoint on that host.

## 2.3 TCP

TCP is connection-oriented and provides a reliable, ordered byte stream. It is suitable for this chat application because the application wants messages to arrive reliably and in order.

**Critical point:** TCP is a byte stream, not a message protocol. A single `read()` may return:

- only part of one application message;
- exactly one message; or
- several messages together.

Therefore, the application must define its own message framing. This guide uses newline-delimited UTF-8 messages for the initial version.

## 2.4 Socket and channel

A socket is a communication endpoint. In Java NIO:

- `ServerSocketChannel` listens for incoming TCP connections.
- `SocketChannel` represents a connected TCP channel.
- `InetSocketAddress` represents an IP address and port combination.

## 2.5 Java NIO

The main NIO concepts are:

- **Channel:** connection to an input/output source or destination.
- **Buffer:** temporary memory used when reading or writing data.
- **Selector:** monitors registered channels and reports which operations are ready.
- **SelectionKey:** represents a channel's registration and interest/ready operations.
- **Non-blocking mode:** a channel operation returns without waiting indefinitely for data or buffer capacity.

For this server, the Selector monitors:
- `OP_ACCEPT` — a client connection is ready to be accepted.
- `OP_READ` — a connected client has data ready to read.
- `OP_WRITE` — a channel can accept queued outgoing data.

## 2.6 Multithreading

A thread is an independent path of execution within a process. A thread pool reuses a limited number of worker threads for tasks.

This design uses:
- one selector/event-loop thread for server network I/O and selector-key changes;
- a bounded `ExecutorService` for appropriate background tasks, such as message validation, logging, or other work that should not block the event loop;
- a client-side background reader or client-side selector so the user interface does not freeze while waiting for incoming messages.

**Do not create one blocking thread per client while presenting the server as a selector-based non-blocking NIO server.** That would mix two different server architectures and weaken the explanation of why Selector is being used.

---

# 3. System architecture

## 3.1 High-level architecture

```text
                 +----------------------+
                 |      Chat Server     |
                 |                      |
                 | ServerSocketChannel  |
                 | Selector             |
                 | Client Registry      |
                 | Message Broadcaster  |
                 | ExecutorService      |
                 +----------+-----------+
                            |
              TCP connection on server port
              /             |             \
             /              |              \
+----------------+ +----------------+ +----------------+
| Alice's Client | |  Bob's Client   | | Charlie Client |
| SocketChannel  | | SocketChannel   | | SocketChannel  |
| Message Sender | | Message Sender  | | Message Sender  |
| Message Reader | | Message Reader  | | Message Reader |
| Chat UI        | | Chat UI         | | Chat UI        |
+----------------+ +----------------+ +----------------+
```

All clients connect to the central server. Clients do not connect directly to each other. A client sends its message to the server, and the server forwards it to the other connected clients.

## 3.2 Example

Assume:

- Server address: `192.168.1.10`
- Server port: `5000`
- Alice connects from one computer.
- Bob connects from another computer.
- Charlie connects from a third computer.

Alice sends:

```text
CHAT|Alice|Hello everyone!
```

The flow is:

1. Alice's client encodes the message as UTF-8 bytes.
2. Alice's `SocketChannel` writes the bytes to the TCP connection.
3. The server Selector reports that Alice's channel is readable.
4. The server reads bytes into Alice's input `ByteBuffer`.
5. The server's framing code extracts the complete message.
6. The server validates/parses the message.
7. The server queues the message for Bob and Charlie.
8. The server writes queued bytes to their channels when those channels are writable.
9. Bob and Charlie's clients read and display the message.

---

# 4. Project package and folder structure

Use Maven and agree on the package structure before different members start coding.

```text
NetworkChatApplication/
│
├── pom.xml
├── README.md
├── docs/
│   ├── protocol.md
│   └── diagrams/
│
└── src/
    ├── main/
    │   └── java/
    │       └── com/
    │           └── networkchat/
    │               ├── Main.java
    │               │
    │               ├── server/
    │               │   ├── ChatServer.java
    │               │   ├── ClientManager.java
    │               │   ├── ClientSession.java
    │               │   └── MessageBroadcaster.java
    │               │
    │               ├── client/
    │               │   ├── ChatClient.java
    │               │   ├── ClientConnection.java
    │               │   └── MessageListener.java
    │               │
    │               ├── network/
    │               │   ├── NetworkConfig.java
    │               │   ├── MessageProtocol.java
    │               │   └── LineFramer.java
    │               │
    │               ├── model/
    │               │   ├── User.java
    │               │   └── ChatMessage.java
    │               │
    │               └── ui/
    │                   ├── LoginFrame.java
    │                   └── ChatFrame.java
    │
    └── test/
        └── java/
            └── com/
                └── networkchat/
```

The UI package is optional. Start with console clients and add Swing after the networking code works.

## 4.1 Class responsibilities

| Class | Responsibility |
|---|---|
| `Main` | Starts the server or client, depending on the selected mode |
| `NetworkConfig` | Stores the default port, buffer sizes and agreed configuration |
| `ChatServer` | Opens the server channel and runs the server lifecycle |
| `ClientSession` | Stores state for one connected client: channel, username, buffers and output queue |
| `ClientManager` | Maintains connected client sessions and username rules |
| `MessageBroadcaster` | Creates broadcast messages and queues them for recipients |
| `ChatClient` | Client application entry point and connection lifecycle |
| `ClientConnection` | Client-side TCP/NIO connection and send/receive operations |
| `MessageListener` | Delivers received messages to the console or UI |
| `MessageProtocol` | Encodes and parses application messages |
| `LineFramer` | Extracts complete newline-delimited messages from a byte stream |
| `User` | Represents a chat user |
| `ChatMessage` | Represents a parsed chat message |
| `LoginFrame` | Optional login window |
| `ChatFrame` | Optional chat window |

---

# 5. Shared application protocol

All members must use exactly the same message format. Put the specification in `docs/protocol.md` and implement shared parsing/encoding in `MessageProtocol`.

## 5.1 Initial message types

Use one line per message, terminated by `\n`.

| Direction | Message | Meaning |
|---|---|---|
| Client → Server | `LOGIN|username` | Request to join |
| Client → Server | `CHAT|message` | Send a chat message; server knows the sender from the connection |
| Client → Server | `LOGOUT` | Request to leave |
| Server → Clients | `JOIN|username` | A user joined |
| Server → Clients | `CHAT|username|message` | A user sent a message |
| Server → Clients | `LEAVE|username` | A user left |
| Server → Client | `ERROR|reason` | An error occurred |

Prefer deriving the sender's username from the server-side client session rather than trusting a username supplied in every chat message. This prevents a client from pretending to be another user.

## 5.2 Delimiter and newline handling

A simple delimiter protocol needs a rule for special characters. For the first version, either:

1. reject `|`, carriage return and newline in usernames/messages; or
2. implement a documented escaping rule; or
3. use a length-prefixed protocol.

For a beginner project, option 1 is easiest. Set maximum username and message lengths and validate them.

## 5.3 Example protocol lines

```text
LOGIN|Alice
CHAT|Hello everyone!
JOIN|Bob
CHAT|Alice|Hello everyone!
LOGOUT
LEAVE|Alice
ERROR|Username already in use
```

The server should send `JOIN` and `LEAVE` notifications to the other connected clients. Decide whether the joining client also receives its own `JOIN` event and document that choice.

---

# 6. Java NIO server design

This is the central technical component of the project.

## 6.1 Server startup

The server should:

1. Open a `Selector`.
2. Open a `ServerSocketChannel`.
3. Configure the server channel as non-blocking.
4. Bind it to the agreed port.
5. Register it with the Selector for `OP_ACCEPT`.
6. Enter the event loop.

Illustrative startup code:

```java
Selector selector = Selector.open();

ServerSocketChannel serverChannel =
        ServerSocketChannel.open();

serverChannel.configureBlocking(false);

serverChannel.bind(
        new InetSocketAddress(5000)
);

serverChannel.register(
        selector,
        SelectionKey.OP_ACCEPT
);
```

This is a teaching snippet, not the complete server. Add exception handling, shutdown handling and resource cleanup in the actual class.

## 6.2 Selector event loop

The event loop repeatedly waits for ready operations:

```java
while (serverRunning) {
    selector.select();

    Iterator<SelectionKey> iterator =
            selector.selectedKeys().iterator();

    while (iterator.hasNext()) {
        SelectionKey key = iterator.next();
        iterator.remove();

        if (!key.isValid()) {
            continue;
        }

        if (key.isAcceptable()) {
            // Accept a new client connection.
        } else if (key.isReadable()) {
            // Read bytes from a connected client.
        } else if (key.isWritable()) {
            // Write queued outgoing bytes.
        }
    }
}
```

The order and exact implementation should be designed carefully. A key can be ready for more than one operation, so do not assume that the `else if` structure is always sufficient for every possible design. Handle the registered/ready operations your implementation supports.

## 6.3 Accepting a client

When the server key is acceptable:

1. Call `serverChannel.accept()`.
2. Check for `null` defensively.
3. Configure the new `SocketChannel` as non-blocking.
4. Create a `ClientSession`.
5. Register the channel with the Selector for `OP_READ`.
6. Attach the session to its `SelectionKey`.

Illustrative code:

```java
SocketChannel clientChannel = serverChannel.accept();

if (clientChannel != null) {
    clientChannel.configureBlocking(false);

    SelectionKey clientKey =
            clientChannel.register(selector, SelectionKey.OP_READ);

    // Create and attach a ClientSession to clientKey.
}
```

## 6.4 Reading data with ByteBuffer

Each client needs its own input buffer. Do not use one shared mutable buffer for every connection.

Typical buffer lifecycle:

```java
int bytesRead = channel.read(inputBuffer);

if (bytesRead == -1) {
    // Client disconnected.
} else if (bytesRead > 0) {
    inputBuffer.flip();

    // Extract complete messages from the bytes.

    inputBuffer.compact();
}
```

Understand the buffer methods:

| Method | Meaning |
|---|---|
| `allocate(size)` | Creates a buffer |
| `put(...)` | Adds bytes to a buffer |
| `get()` | Reads bytes from a buffer |
| `flip()` | Changes from writing mode to reading mode |
| `clear()` | Resets the buffer for writing, discarding current content |
| `compact()` | Preserves unread bytes and makes space for more input |

Use `compact()` when incomplete message bytes must be retained for the next read.

## 6.5 Message framing

Do not parse each `read()` result as one message. Maintain per-client input state.

For newline-delimited messages:

1. Read bytes into the client's input buffer.
2. Flip the buffer.
3. Scan for newline delimiters.
4. Extract every complete line.
5. Leave any incomplete trailing line in the buffer.
6. Compact the buffer.
7. Decode complete lines as UTF-8 and parse them.

Also define a maximum frame size. If a client sends an extremely long line without a newline, disconnect or reject it rather than allowing unbounded buffer growth.

## 6.6 Writing data safely

A non-blocking `SocketChannel.write(buffer)` may write fewer bytes than the buffer contains. Therefore, each client session needs an outgoing queue.

Suggested approach:

- Encode each outgoing protocol line as UTF-8.
- Wrap it in a `ByteBuffer`.
- Add the buffer to the recipient's outgoing queue.
- Enable `OP_WRITE` on that client's key.
- When the key becomes writable, write from the head buffer.
- If the buffer has remaining bytes, keep it queued and leave `OP_WRITE` enabled.
- When the buffer is fully written, remove it.
- When the queue becomes empty, remove `OP_WRITE` from the interest set.

Do not repeatedly register `OP_WRITE` when there is no data to send; writable sockets are often ready continuously and can cause a busy loop.

## 6.7 Disconnection handling

When `read()` returns `-1`, or a channel fails:

1. Cancel its SelectionKey.
2. Close the SocketChannel.
3. Remove its session from the client registry.
4. Release its username.
5. Broadcast a `LEAVE|username` notification to remaining clients.
6. Continue serving other clients.

---

# 7. Client-side design

The client should connect to the server, send a login request, send chat messages, and display received messages.

## 7.1 Connect to the server

Illustrative connection code:

```java
SocketChannel channel = SocketChannel.open();

channel.configureBlocking(false);

channel.connect(
        new InetSocketAddress(serverIp, serverPort)
);

while (!channel.finishConnect()) {
    // In a simple console prototype, retry/check until connected.
}
```

For a production-quality non-blocking client, register `OP_CONNECT` with a client Selector and call `finishConnect()` when the key becomes connectable. Do not busy-spin in the final implementation.

## 7.2 Send a login message

After the connection is established, send:

```text
LOGIN|Alice\n
```

Encode the line using UTF-8. Handle partial writes with a queue or a write loop appropriate to the client's blocking/non-blocking strategy.

## 7.3 Send chat messages

The user enters a message. The client validates it, encodes the agreed protocol line and sends it through the channel.

For example:

```text
CHAT|Hello everyone!\n
```

## 7.4 Receive messages

Incoming messages should be read in the background so that the user interface remains responsive.

For a Swing application:
- Do not perform blocking network reads on the Swing Event Dispatch Thread.
- Deliver UI updates using `SwingUtilities.invokeLater(...)`.
- Ensure that the client connection is closed when the user disconnects or closes the window.

A client can use a separate background reader thread with a clearly documented strategy, or a client-side Selector. The server must still demonstrate its NIO Selector-based multi-client handling.

---

# 8. Multithreading design

## 8.1 Why use a thread pool?

The assignment asks each member to implement a network programming concept. A thread pool provides a concrete way to demonstrate multithreading.

Illustrative creation:

```java
ExecutorService executor =
        Executors.newFixedThreadPool(4);
```

The number `4` is an example; select and explain a reasonable value for your implementation.

## 8.2 What should run on the Selector thread?

Keep these operations on the server's Selector thread:

- `selector.select()` and selected-key processing;
- accepting connections;
- channel reads and writes;
- registering/cancelling keys;
- changing SelectionKey interest operations;
- updating selector-owned connection state.

This makes the server easier to reason about and avoids unsafe concurrent changes to SelectionKeys.

## 8.3 What can run on worker threads?

Use worker threads for tasks that may take time and do not need to manipulate Selector keys directly, such as:

- message validation or transformation;
- logging;
- non-network business logic;
- other deliberately isolated background tasks.

For a small chat application, parsing a short line may be fast enough to do on the event loop. Do not add a thread pool merely to claim multithreading if it makes the architecture unnecessarily complicated. Make sure the pool performs a real, explainable task.

## 8.4 Safe communication from workers to the Selector

If a worker needs to request a network action:

1. Put a command/result in a thread-safe queue.
2. Call `selector.wakeup()`.
3. The Selector thread wakes up.
4. The Selector thread processes the queue and updates keys/queues.

Never let worker threads independently change a channel's SelectionKey interest operations without coordination.

## 8.5 Client registry and broadcasting

The server should maintain a collection of connected client sessions.

A `ClientSession` should contain at least:

- connected `SocketChannel`;
- username after successful login;
- input buffer;
- outgoing buffer queue;
- connection/authentication state;
- reference to or access to its SelectionKey, managed according to the selector-thread ownership rule.

When Alice sends a chat message, the broadcaster should queue the outgoing message for each intended recipient. The Selector thread performs the actual non-blocking writes.

---

# 9. Five-member work allocation

The following allocation assumes five group members. Each member must write, test and understand their own contribution. Each member should be able to point to the relevant code during the physical evaluation.

## Member 1 — Server startup, TCP, IP address and port

### Main responsibility

Build the server bootstrap: configuration, server channel, bind address and port, startup/shutdown lifecycle.

### Concepts

- Client-server architecture
- TCP
- IP addressing
- Port numbers
- `InetSocketAddress`
- `ServerSocketChannel`

### Files to own

```text
network/NetworkConfig.java
server/ChatServer.java
```

### Step-by-step coding guide

**Step 1 — Create configuration**

In `NetworkConfig.java`, define shared constants such as:

```java
public final class NetworkConfig {
    public static final int PORT = 5000;
    public static final int BUFFER_SIZE = 4096;

    private NetworkConfig() {
    }
}
```

Agree with the group whether the server binds to `0.0.0.0` (all local interfaces) or a particular interface. Clients should connect to the server's actual LAN IP, not to `0.0.0.0`.

**Step 2 — Create the server channel**

In `ChatServer.java`:
- open a Selector;
- open a ServerSocketChannel;
- configure it as non-blocking;
- bind it to the chosen port;
- register it with the Selector for `OP_ACCEPT`.

**Step 3 — Hand over event-loop responsibility**

Coordinate with Member 3. Member 1 owns server bootstrap and lifecycle; Member 3 owns Selector readiness processing and NIO event-loop implementation. Avoid two competing `main` methods or two event loops.

**Step 4 — Add shutdown handling**

Close the Selector and server channel in a `finally` block or an equivalent controlled shutdown method. Report a useful error if the port is already in use.

### Test checklist

- Server starts successfully.
- Server listens on the agreed port.
- A client can connect.
- A second client can connect.
- Server reports a useful error when the port is already occupied.
- Server can be stopped cleanly.

### Two-minute demonstration

1. Show the server configuration.
2. Start the server.
3. Explain the server IP/bind address and port.
4. Show a client connecting.
5. Explain why TCP is being used.

### Questions to prepare for

- What is the difference between an IP address and a port?
- Why use TCP for a chat application?
- What is the role of `ServerSocketChannel`?
- What is the difference between localhost and a LAN IP?

---

## Member 2 — Client connection and SocketChannel

### Main responsibility

Implement the client-side connection, login request, sending messages and clean disconnection.

### Concepts

- Client-server architecture
- TCP connection
- Socket endpoint
- `SocketChannel`
- `InetSocketAddress`
- Network input/output

### Files to own

```text
client/ChatClient.java
client/ClientConnection.java
```

### Step-by-step coding guide

**Step 1 — Collect connection details**

The client needs:
- server IP/host;
- server port;
- username.

Use the same port constant or configuration agreed by the group.

**Step 2 — Open the channel**

Create a `SocketChannel`, configure the agreed blocking/non-blocking strategy and connect to the server's `InetSocketAddress`.

If the client uses non-blocking connect, use `OP_CONNECT` and `finishConnect()` correctly rather than busy-spinning.

**Step 3 — Login**

After connecting, send the agreed login line, for example:

```text
LOGIN|Alice\n
```

Wait for or handle the server's acceptance/error response according to the protocol agreed by the group.

**Step 4 — Send chat messages**

Implement a method such as:

```java
sendChatMessage(String message)
```

It should validate the message, encode it as UTF-8 and write it using the agreed partial-write-safe strategy.

**Step 5 — Disconnect**

Send `LOGOUT` where possible, then close the channel. Handle server shutdown or connection loss without crashing.

**Step 6 — Coordinate receiving**

Agree with Member 5 how incoming messages are passed to the console or UI. Do not let the UI thread block waiting for network input.

### Test checklist

- Connect to a server on localhost.
- Connect to a server using its LAN IP.
- Send a login message.
- Send a chat message.
- Handle server shutdown.
- Close the client cleanly.

### Two-minute demonstration

Connect a client to the running server, show the connection and send a message. Explain how the SocketChannel represents the client's TCP connection.

### Questions to prepare for

- What is a SocketChannel?
- Why does a client need a server IP and port?
- What is the purpose of `connect()`?
- Why must partial writes be handled?

---

## Member 3 — Java NIO: Selector, SelectionKey and ByteBuffer

### Main responsibility

Implement the server's NIO event loop. This is the main demonstration of the assignment's Java NIO requirement.

### Concepts

- Java NIO
- `Selector`
- `SelectionKey`
- `ServerSocketChannel`
- `SocketChannel`
- `ByteBuffer`
- Non-blocking I/O
- `OP_ACCEPT`, `OP_READ`, `OP_WRITE`

### Files to own

```text
server/ChatServer.java       (event-loop portion, coordinated with Member 1)
server/ClientSession.java
network/LineFramer.java
```

### Step-by-step coding guide

**Step 1 — Open the Selector**

Create a Selector and register the server channel with `OP_ACCEPT`. Coordinate with Member 1 so the server channel is opened and bound only once.

**Step 2 — Implement the event loop**

Use `selector.select()` to wait for readiness events. Iterate over `selectedKeys()`, and remove each key from the selected set after retrieving it.

**Step 3 — Handle acceptable keys**

When a key is acceptable:
- accept the new SocketChannel;
- configure it as non-blocking;
- create a ClientSession;
- register the channel for `OP_READ`;
- attach the session to the key.

**Step 4 — Handle readable keys**

When a client is readable:
- read into that client's input ByteBuffer;
- detect `-1` as disconnection;
- flip the buffer;
- extract all complete newline-delimited frames;
- preserve any incomplete trailing bytes;
- compact the buffer;
- pass complete messages to the agreed protocol handler.

**Step 5 — Handle writable keys**

When a client is writable:
- take the first buffer from its outgoing queue;
- write as much as possible;
- keep the remaining bytes queued if the buffer still has data;
- remove fully written buffers;
- disable `OP_WRITE` when the queue is empty.

**Step 6 — Handle errors**

Catch per-client I/O failures, clean up the relevant channel and session, and keep the server alive for other clients.

### Test checklist

- Multiple clients connect through one Selector.
- One read containing multiple complete lines is handled correctly.
- A message split across multiple reads is reconstructed correctly.
- Partial writes do not truncate messages.
- A disconnected client is removed.
- Other clients continue communicating.

### Two-minute demonstration

Show the Selector registrations and event loop. Explain `OP_ACCEPT`, `OP_READ`, `OP_WRITE`, `ByteBuffer.flip()` and `compact()`. Show multiple clients connected at the same time.

### Questions to prepare for

- What is Java NIO?
- What does a Selector do?
- What is the difference between blocking and non-blocking I/O?
- Why is ByteBuffer required?
- Why can't one read be assumed to equal one message?
- What is the difference between `flip()`, `clear()` and `compact()`?

---

## Member 4 — Multithreading, client management and broadcasting

### Main responsibility

Implement the connected-client registry, username management, message broadcasting and a real thread-pool task.

### Concepts

- Multithreading
- `ExecutorService`
- Thread pool
- Concurrent client management
- Shared state and thread safety
- Broadcasting

### Files to own

```text
server/ClientManager.java
server/MessageBroadcaster.java
```

Coordinate with Member 3 on the ClientSession design and selector-thread ownership.

### Step-by-step coding guide

**Step 1 — Manage client sessions**

Implement methods with responsibilities such as:

```java
addClient(...)
removeClient(...)
findClient(...)
getConnectedClients(...)
```

Choose a collection appropriate to the ownership model. If the Selector thread alone owns the registry, document that rule and do not access it unsafely from worker threads.

**Step 2 — Manage usernames**

When a client logs in:
- validate the username;
- reject an empty or invalid username;
- reject duplicates;
- associate the accepted username with that connection.

Do not trust the username in a chat message as the identity of the sender. Use the authenticated session.

**Step 3 — Create the thread pool**

Use a bounded ExecutorService for a real background responsibility such as logging or message validation/transformation. Explain why the task is offloaded and why the pool has a finite size.

**Step 4 — Implement broadcasting**

When the server receives a valid chat message:
- identify the sender from its session;
- create the server-side broadcast line;
- queue it for each intended recipient;
- ask the Selector thread to perform the actual channel writes.

**Step 5 — Handle join and leave**

Broadcast join and leave notifications to remaining clients. Remove disconnected users from the registry.

**Step 6 — Shut down workers**

Call the appropriate executor shutdown method during server shutdown. Avoid leaving worker threads running after the server exits.

### Thread-safety rule

The Selector thread owns channel I/O and SelectionKey changes. If a worker thread needs to request a network update, it should add a command to a thread-safe queue and call `selector.wakeup()`. The Selector thread processes the command and updates the key/queue.

### Test checklist

- At least three clients can connect.
- Duplicate usernames are rejected.
- A message reaches all intended recipients.
- One client leaving does not disconnect the others.
- Concurrent incoming messages are not mixed or lost.
- Worker threads shut down with the server.

### Two-minute demonstration

Connect three clients. Send a message from one client and show it arriving at the other clients. Explain the thread pool and how the server avoids unsafe concurrent writes.

### Questions to prepare for

- What is multithreading?
- What is an ExecutorService?
- Why use a thread pool rather than creating unlimited threads?
- What is a race condition?
- Why should only the Selector thread change SelectionKey interests?

---

## Member 5 — Message protocol, UI integration and testing

### Main responsibility

Define the shared application protocol, implement message encoding/parsing, integrate the client with the UI and prepare end-to-end tests.

### Concepts

- Application-level protocol
- Network input/output
- UTF-8 encoding
- Message framing
- Client-server message exchange
- Integration testing

### Files to own

```text
network/MessageProtocol.java
model/ChatMessage.java
ui/LoginFrame.java
ui/ChatFrame.java
README.md
```

The UI files are optional if the group chooses to demonstrate a console client.

### Step-by-step coding guide

**Step 1 — Finalize the protocol with everyone**

Write the exact message types and examples in `docs/protocol.md`. Get agreement from all members before implementation.

**Step 2 — Implement encoding and parsing**

Implement clear methods such as:

```java
encodeLogin(username)
encodeChat(message)
encodeLogout()
parseServerMessage(line)
```

Use UTF-8. Validate message type, field count, username rules and maximum message length.

**Step 3 — Create the message model**

`ChatMessage` can contain:
- message type;
- sender username;
- message body;
- timestamp (optional).

Do not add unnecessary complexity before the basic protocol works.

**Step 4 — Integrate the UI**

If using Swing, create:
- login screen with username, server IP and port;
- chat window with message history;
- message input field;
- Send button;
- connection status;
- Disconnect button.

Keep network reads/writes off the Swing Event Dispatch Thread. Update visible components on the EDT using `SwingUtilities.invokeLater(...)`.

**Step 5 — Create the test plan**

Test login, duplicate usernames, chat broadcasting, join/leave notifications, invalid messages, disconnections, and multiple clients.

**Step 6 — Prepare README instructions**

Document:
- Java version;
- how to build;
- how to start the server;
- how to start multiple clients;
- how to connect over localhost;
- how to connect over LAN;
- known limitations.

### Test checklist

- Login works.
- Duplicate/invalid usernames are handled.
- Chat messages display correctly.
- Join/leave notifications display.
- The UI remains responsive.
- Server errors are shown clearly.
- The application works with multiple clients.

### Two-minute demonstration

Show the login screen, connect several clients, send a message and show it appearing on the other clients. Explain the protocol format and how the client/server interpret messages.

### Questions to prepare for

- What is an application protocol?
- Why does the application need message framing?
- What is UTF-8?
- Why should network operations not block the Swing UI thread?
- How are messages encoded and parsed?

---

# 10. How members should integrate their code

The group should not wait until the final day to combine the code. Integrate after each milestone.

## 10.1 Agree on these interfaces first

Before coding, agree on the names and responsibilities of these operations. The exact method signatures can be adjusted, but all members must use the same design.

| Component | Agreed responsibility |
|---|---|
| `NetworkConfig` | Shared port, buffer size and limits |
| `MessageProtocol` | Encode/decode protocol lines |
| `ClientSession` | Per-client state and buffers |
| `ClientManager` | Add/remove/find clients |
| `MessageBroadcaster` | Queue messages for recipients |
| `ChatServer` | Server startup and Selector event loop |
| `ClientConnection` | Client connection and sending |
| `MessageListener` | Deliver received messages to UI/console |

## 10.2 Suggested integration sequence

1. Member 1 starts a server that binds to the agreed port.
2. Member 2 connects one client to that server.
3. Member 3 integrates Selector-based accept/read handling.
4. Member 5 agrees and integrates the protocol framing/parsing.
5. Member 4 adds client management and broadcasting.
6. All members test with two clients.
7. Add three or more clients and test concurrency.
8. Add the UI after the console version works.
9. Test LAN connectivity on separate computers.
10. Freeze the code and prepare the report/demo.

## 10.3 Git collaboration (recommended)

Use one shared Git repository if possible.

Suggested branches:

```text
main
member-1-server
member-2-client
member-3-nio
member-4-multithreading
member-5-protocol-ui
```

Each member should commit their work regularly and create a pull request or merge request. Agree on package/class contracts before merging. Do not have two members independently rewrite the same class.

---

# 11. Development milestones and schedule

The deadline is 26 November 2026. Work backwards from that date and reserve time for integration, testing and report preparation.

| Milestone | Deliverable |
|---|---|
| 1. Project setup | Maven project, packages, configuration and protocol agreement |
| 2. Basic TCP | Server starts and one client connects |
| 3. NIO server | Selector accepts and reads multiple clients |
| 4. Protocol | Complete messages are framed and parsed correctly |
| 5. Broadcasting | One client message reaches other clients |
| 6. Multithreading | ExecutorService performs a real background task safely |
| 7. User experience | Console or Swing client is usable |
| 8. Testing | Multi-client and failure tests pass |
| 9. Documentation | README and report PDF complete |
| 10. Submission rehearsal | ZIP verified and ten-minute demo rehearsed |

**Do not leave integration until the last day.** The NIO event loop, framing and partial-write handling are the areas most likely to require debugging.

---

# 12. Testing plan

Run the following tests and record the actual result, expected result and evidence (screenshot or log).

| Test | Procedure | Expected result |
|---|---|---|
| T01 | Start server | Server binds and reports ready |
| T02 | Connect one client | Connection accepted |
| T03 | Connect three clients | All remain connected |
| T04 | Send chat message | Intended recipients receive it |
| T05 | Connect duplicate username | Server rejects duplicate |
| T06 | Disconnect one client | Other clients remain connected |
| T07 | Stop server while clients are connected | Clients detect connection loss |
| T08 | Send empty message | Rejected or handled according to documented rule |
| T09 | Send message containing delimiter | Rejected or escaped according to protocol |
| T10 | Send a long message | Maximum-size policy is enforced |
| T11 | Send several messages quickly | No message is silently lost or merged |
| T12 | Test split TCP frame | Incomplete data is retained until complete |
| T13 | Test several frames in one read | All complete frames are processed |
| T14 | Connect over LAN | Client on another computer connects using server LAN IP |
| T15 | Stop server cleanly | Channels, Selector and executor are closed |

Do not claim that a test passed until the group has actually run it.

---

# 13. Physical evaluation: ten-minute demonstration

The assignment gives each member up to two minutes and each group ten minutes. The following plan assumes five members.

| Time | Member | Demonstration |
|---|---|---|
| 0:00–2:00 | Member 1 | Start server; explain TCP, IP address, port and ServerSocketChannel |
| 2:00–4:00 | Member 2 | Connect a client; explain SocketChannel and client-server connection |
| 4:00–6:00 | Member 3 | Explain Selector, SelectionKey, ByteBuffer and non-blocking I/O |
| 6:00–8:00 | Member 4 | Show multiple clients and broadcasting; explain ExecutorService |
| 8:00–10:00 | Member 5 | Show login, chat UI/protocol, message exchange and test evidence |

## Recommended live demonstration

Before the evaluation:

1. Start the server on one computer.
2. Find and record its actual LAN IP address.
3. Connect at least two clients, preferably on separate computers.
4. Log in using different usernames.
5. Send a message from one client.
6. Show it arriving at the other client(s).
7. Disconnect one client.
8. Show that the other clients remain connected and receive the leave notification.

Have a localhost fallback ready in case the classroom Wi-Fi or firewall blocks LAN connections.

---

# 14. Project report structure

Submit the report as a PDF inside the same ZIP as the source project.

Suggested structure:

1. Cover page
2. Abstract
3. Introduction
4. Problem statement
5. Objectives
6. Requirements and scope
7. Technologies and tools
8. Networking concepts
   - Client-server architecture
   - TCP/IP
   - IP addressing and port numbers
   - Socket communication
   - Java NIO
   - Selector and SelectionKey
   - ByteBuffer
   - Non-blocking I/O
   - Multithreading and ExecutorService
   - Application protocol and message framing
9. System architecture
10. UML diagrams
    - Use case diagram
    - Class diagram
    - Sequence diagram for sending a message
11. Implementation
12. Testing methodology and results
13. Individual member contributions
14. Limitations and future improvements
15. Conclusion
16. References

Include screenshots of the real application and short, relevant code extracts. Explain what the code does rather than pasting large amounts of source code into the report.

---

# 15. Final submission checklist

## Application

- [ ] Server starts and stops correctly.
- [ ] Multiple clients can connect.
- [ ] Login and username validation work.
- [ ] Messages are broadcast correctly.
- [ ] Join and leave notifications work.
- [ ] Java NIO Selector is implemented.
- [ ] ServerSocketChannel and SocketChannel are implemented.
- [ ] ByteBuffer is used correctly.
- [ ] Non-blocking I/O is demonstrated.
- [ ] Partial reads and writes are handled.
- [ ] Multithreading has a real, explainable role.
- [ ] Disconnections are handled safely.
- [ ] LAN test has been attempted and result recorded.

## Documentation

- [ ] README contains build and run instructions.
- [ ] Report PDF is complete.
- [ ] Architecture and sequence diagrams are included.
- [ ] Test results are recorded.
- [ ] Each member's individual contribution is described.

## ZIP and Moodle

- [ ] Complete project source code is included.
- [ ] Report PDF is included.
- [ ] ZIP opens and contains the expected files.
- [ ] One designated member submits the ZIP through Moodle.
- [ ] Submission is completed before 26 November 2026.

---

# 16. Common mistakes to avoid

1. **Treating one TCP read as one message.** TCP is a byte stream. Implement message framing.
2. **Ignoring partial writes.** A non-blocking write may not send the entire buffer.
3. **Sharing one ByteBuffer between all clients.** Each connection needs its own input state.
4. **Blocking the Selector thread.** Slow work can prevent the server from processing other ready clients.
5. **Changing SelectionKey interests from arbitrary worker threads.** Coordinate changes through a queue and wake up the Selector thread.
6. **Enabling OP_WRITE permanently.** Enable it when there is queued data and disable it when the queue is empty.
7. **Updating Swing components from a background thread.** Use the Swing Event Dispatch Thread for UI updates.
8. **Trusting the username in every chat message.** Associate the username with the authenticated connection/session.
9. **Testing only on localhost.** Also test on a LAN if the evaluation expects separate computers.
10. **Adding too many features before the core works.** First make connection, login, message exchange and broadcast reliable.

---

# 17. Recommended implementation order

For the group, the most reliable order is:

```text
Project setup
    |
    v
Server binds to IP/port
    |
    v
One client connects using TCP
    |
    v
Selector accepts and monitors channels
    |
    v
ByteBuffer reads and message framing work
    |
    v
Client sends a complete chat message
    |
    v
Server parses and broadcasts it
    |
    v
Multiple clients receive messages
    |
    v
Client management and multithreading
    |
    v
Disconnect handling and testing
    |
    v
Optional Swing interface
    |
    v
Report, ZIP and demonstration
```

## Final advice

The strongest version of this assignment is not necessarily the one with the most features. It is the one where every group member can explain and demonstrate their own code, and where the application genuinely uses TCP, Java NIO, buffers, selectors, non-blocking I/O and multithreading.

Build a working console-based chat application first. Once the network layer is stable, add the frontend. This keeps the project focused on the Network Programming learning outcomes and makes the physical evaluation much easier to prepare for.
