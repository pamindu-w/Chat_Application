# Member 3 — Java NIO: Selector, Channels and ByteBuffer

## Individual responsibility
Own the server's NIO event loop and demonstrate the required Java New I/O concepts. This is the central technical contribution.

## Concepts to demonstrate
- Java NIO
- `ServerSocketChannel`
- `SocketChannel`
- `Selector`
- `SelectionKey`
- `ByteBuffer`
- Non-blocking I/O
- Readiness events: `OP_ACCEPT`, `OP_READ`, and `OP_WRITE`

## Files
- `network/NIOManager.java` or `server/ChatServer.java` (agree with Member 1)
- `network/LineFramer.java` (coordinate protocol framing with Member 5)

## Implementation tasks
1. Open a `Selector`.
2. Register the non-blocking server channel with `OP_ACCEPT`.
3. Run an event loop using `selector.select()`.
4. Iterate selected keys and remove each processed key from the selected-key set.
5. For acceptable keys, accept a client, configure its channel non-blocking, and register it for `OP_READ`.
6. For readable keys, read bytes into a per-client input buffer.
7. Decode UTF-8 and extract complete newline-delimited messages. Retain incomplete bytes for the next read.
8. For writable keys, write queued buffers until the channel returns zero or all data is written.
9. Handle `read()` returning `-1` as client disconnection.
10. Close/cancel invalid or disconnected channels safely.

## Buffer lifecycle to explain
- `allocate()` creates a buffer.
- `put()` adds bytes.
- `flip()` switches from writing into the buffer to reading from it.
- `get()` reads bytes.
- `compact()` preserves unread bytes and prepares the buffer for more input.
- `clear()` discards current content and prepares the whole buffer for writing.

## Critical correctness points
- TCP has no message boundaries: a read can return part of a line or several lines.
- A non-blocking write can be partial. Queue remaining bytes and register `OP_WRITE` only while output is pending.
- Do not block the selector thread on slow operations.
- Keep per-client buffers and output queues; do not share one mutable buffer among clients.
- If a worker thread produces outgoing data, enqueue it safely and call `selector.wakeup()`. Make selector-key changes on the selector thread.

## Acceptance tests
- Server handles multiple clients with one selector event loop.
- Messages split across reads are reconstructed correctly.
- Multiple messages received in one read are both processed.
- Large messages are handled within the chosen protocol limit.
- Disconnected clients are removed without stopping the server.
- Partial writes do not truncate messages.

## Individual two-minute demonstration
Show the `Selector`, registration of `OP_ACCEPT`/`OP_READ`, a `ByteBuffer` read cycle, and explain non-blocking readiness. Demonstrate at least two clients connected simultaneously.

## Explain in the report
Include a short sequence diagram or flow diagram of the selector event loop and a table describing the NIO classes and their roles.
