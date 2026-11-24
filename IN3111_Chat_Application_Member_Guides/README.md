# IN3111 Network Programming — Group Chat Application

## Project
**TCP-based Java Chat Application using Java NIO and Multithreading**

These five member guides assume a five-person group. Each member has an individually demonstrable networking contribution. The guides describe the target design and integration contracts; agree on final class names and method signatures together before coding.

## Shared technical decisions
- Java, Maven, package root: `com.networkchat`
- TCP communication over an IP address and port (default port: `5000`)
- NIO server: `ServerSocketChannel`, `SocketChannel`, `Selector`, `SelectionKey`, `ByteBuffer`
- Non-blocking server event loop; do not create one blocking thread per client
- `ExecutorService` for bounded background work where appropriate
- Swing UI can be added after console-based networking works
- Text protocol: newline-delimited UTF-8 messages, with escaping or a clearly defined delimiter rule

## Shared protocol proposal
For the first prototype, use one line per message:
- Client to server: `LOGIN|username`
- Client to server: `CHAT|username|message`
- Client to server: `LOGOUT|username`
- Server to clients: `JOIN|username`
- Server to clients: `CHAT|username|message`
- Server to clients: `LEAVE|username`
- Server to client: `ERROR|reason`

**TCP framing requirement:** TCP is a byte stream. One `read()` is not guaranteed to contain exactly one complete line. Maintain per-connection input state, extract every complete newline-terminated message, and retain incomplete trailing bytes. Handle partial writes too; queue outgoing buffers and keep `OP_WRITE` enabled only while data remains.

## Integration rules
1. Use one agreed protocol document and constants; do not invent different message formats in each module.
2. The server owns the authoritative username-to-connection mapping.
3. Only the selector/event-loop thread should change selector registrations and interest operations. Worker threads should return results through a thread-safe queue and wake the selector with `selector.wakeup()`.
4. Never assume a write sends the entire buffer.
5. On disconnect, cancel the key, close the channel, remove the client, and notify remaining users.
6. Test first with localhost, then with multiple computers on the same LAN.
7. Do not expose a server to the public internet for this assignment; use a trusted LAN.

## Suggested repository layout
```text
src/main/java/com/networkchat/
  server/       ChatServer, ClientManager, ClientSession
  client/       ChatClient, ClientConnection
  network/      NetworkConfig, MessageProtocol, LineFramer
  model/        User, ChatMessage
  ui/           LoginFrame, ChatFrame       (optional)
```

## Submission checklist
- Complete Maven project source code
- `README.md` with build/run instructions and tested environment
- Report PDF with architecture, diagrams, implementation, tests, limitations, and individual contributions
- One ZIP containing the project and report PDF
- Each member prepared for a maximum two-minute individual demonstration
