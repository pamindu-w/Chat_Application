# Member 5 — Message Protocol, UI Integration and Testing

## Individual responsibility
Define and test the application-level message format, integrate the client with the user interface, and prepare reproducible end-to-end tests.

## Concepts to demonstrate
- Network application protocol
- Input/output over network channels
- Message encoding and decoding (UTF-8)
- Client-server request/response flow
- End-to-end network testing

## Files
- `network/MessageProtocol.java`
- `model/ChatMessage.java`
- `ui/LoginFrame.java` and `ui/ChatFrame.java` (if Swing is selected)
- `README.md` and test notes

## Protocol proposal
Agree with all members before coding:
- `LOGIN|username`
- `CHAT|username|message`
- `LOGOUT|username`
- `JOIN|username`
- `LEAVE|username`
- `ERROR|reason`

Terminate each message with `\n` and encode/decode using UTF-8. Define how delimiter characters inside usernames/messages are handled (for example, escape them or use a length-prefixed format). Never rely on arbitrary `read()` boundaries as message boundaries.

## Implementation tasks
1. Write a protocol specification shared by client and server developers.
2. Implement message parsing/validation and serialization.
3. Define maximum username and message lengths.
4. Integrate the client networking API with the UI.
5. Ensure UI updates happen on Swing's Event Dispatch Thread (`SwingUtilities.invokeLater`) if Swing is used.
6. Display connection status, join/leave notices, incoming messages, and errors.
7. Create a test plan and record results.
8. Prepare README instructions for starting the server and multiple clients.

## Suggested UI
- Login screen: username, server IP, port, Connect button.
- Chat screen: message history, online status, message input, Send button, Disconnect button.
- Keep the interface simple; networking correctness is more important than visual complexity.

## Acceptance tests
- Valid login is accepted; invalid/duplicate login is rejected as agreed.
- Chat messages appear on all intended clients.
- Empty or oversized messages are handled safely.
- Join/leave notifications display correctly.
- UI remains responsive during network communication.
- Test both localhost and LAN connections.

## Individual two-minute demonstration
Show the login form, connect two or three clients, send a message, and show the received message and join/leave notifications. Explain the protocol format and how the application frames messages over TCP.

## Report contribution
Prepare the protocol specification, UI screenshots, test cases/results, and a summary of the end-to-end application flow.
