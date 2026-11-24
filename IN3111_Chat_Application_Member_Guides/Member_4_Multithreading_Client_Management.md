# Member 4 — Multithreading and Multiple-Client Management

## Individual responsibility
Implement the concurrency/background-processing part and help the server manage multiple connected users safely.

## Concepts to demonstrate
- Multithreading
- `ExecutorService`
- Thread pool
- Concurrent client handling
- Thread safety and shared state
- Message broadcasting to multiple clients

## Files
- `server/ClientManager.java`
- `server/MessageBroadcaster.java`
- Optional `server/ChatTask.java`

## Architecture agreement
The server's Selector event loop remains responsible for non-blocking network I/O and channel/key operations. Use a bounded `ExecutorService` for suitable background tasks (for example, validation, logging, or message processing). If a worker needs the selector thread to perform a write or modify a key, it should place a command in a thread-safe queue and call `selector.wakeup()`; the selector thread then applies it.

Do not create one blocking thread per client while claiming the server is using a selector-based NIO architecture.

## Implementation tasks
1. Implement a client registry that maps usernames or session IDs to client sessions.
2. Prevent duplicate usernames or define a clear duplicate-name policy.
3. Use an `ExecutorService` with a bounded number of worker threads for background tasks.
4. Implement message broadcast logic that targets all connected clients except the sender (or agree on whether the sender also receives an echo).
5. Ensure shared client collections are accessed safely. Prefer selector-thread ownership for connection state, or use appropriate concurrent structures and synchronization.
6. Handle users joining and leaving.
7. Shut down the executor cleanly when the server stops.

## Acceptance tests
- At least three clients can connect concurrently.
- A message from one client reaches all intended recipients.
- Two messages arriving close together are not lost or mixed.
- Duplicate usernames are handled predictably.
- One client disconnecting does not affect other clients.
- Server shutdown stops worker threads.

## Individual two-minute demonstration
Connect three clients. Send a message from one and show the other clients receive it. Explain the role of the thread pool and why shared state needs safe coordination.

## Explain in the report
- Difference between concurrency and parallelism at a high level.
- Why a thread pool is preferable to creating unlimited threads.
- Which parts are handled by the selector thread and which by worker threads.
- How the design avoids unsafe concurrent channel writes.
