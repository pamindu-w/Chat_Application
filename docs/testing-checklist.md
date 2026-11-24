# Testing Checklist

Record the date, environment, evidence and actual result before marking a test passed.

| ID | Test | Expected result | Status / evidence |
|---|---|---|---|
| T01 | Start server | Binds and reports ready | In progress (Member 1) |
| T02 | Connect one client | Connection accepted | In progress (Member 1) |
| T03 | Connect three clients | All remain connected | Not run |
| T04 | Send chat | Intended recipients receive it | Not run |
| T05 | Duplicate username | Rejected predictably | Not run |
| T06 | Disconnect one client | Others remain connected | Not run |
| T07 | Stop server | Clients detect loss cleanly | In progress (Member 1) |
| T08 | Empty/oversized input | Rejected safely | Not run |
| T09 | Delimiter input | Rejected safely | Not run |
| T10 | Split TCP frame | Reassembled correctly | Not run |
| T11 | Multiple frames in one read | All frames processed | Not run |
| T12 | Partial write | No truncation | Not run |
| T13 | LAN connection | Remote client connects | In progress (Member 1) |
| T14 | Clean shutdown | Selector, channels and executor close | In progress (Member 1) |
