# Application Protocol v1

Messages are UTF-8, terminated by `\n`, and transported over a TCP byte stream. A single `read()` is not a message boundary; use `LineFramer` per connection.

## Messages

| Direction | Format | Meaning |
|---|---|---|
| Client → server | `LOGIN|username` | Request authentication |
| Client → server | `CHAT|message` | Send a message as the authenticated session |
| Client → server | `LOGOUT` | Request disconnect |
| Server → clients | `JOIN|username` | User joined |
| Server → clients | `CHAT|username|message` | Chat broadcast |
| Server → clients | `LEAVE|username` | User left |
| Server → client | `ERROR|reason` | Request rejected or server error |

## Validation rules

- Username: 1–24 characters.
- Chat message: 1–512 characters.
- Reject `|`, `\r`, and `\n` in username/message fields for v1.
- Duplicate usernames are rejected.
- The server derives the sender username from the authenticated `ClientSession`.
- The joining client receives its own successful `JOIN`; the sender does not receive its own chat echo. Change this only by group agreement.
