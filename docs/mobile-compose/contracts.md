# MindSpark API & Protocol Contracts

This document contains the verified REST and WebSocket contracts, coordinate rules, and runtime guarantees extracted directly from the backend controllers (`ApiModels.java`, `RoomController`, etc.), `RoomWebSocketHandler.java`, and Angular canvas logic (`workspace-canvas.ts`, `drawing-hit-test.ts`, `idea-position-guard.ts`, `drawing-position-guard.ts`).

---

## 1. User

### `POST /api/users/sync`
Syncs or creates the user record from the Keycloak JWT.
* **Headers:** `Authorization: Bearer <token>`
* **Request:** `{}`
* **Response (200 OK):**
```json
{
  "id": 1,
  "username": "ilyes",
  "email": "ilyes@example.com"
}
```

### `GET /api/health`
Health check endpoint.
* **Request:** none
* **Response (200 OK):**
```json
{
  "status": "UP"
}
```

---

## 2. Rooms

### Endpoints
* `GET /api/rooms` — List rooms owned by the authenticated user.
* `POST /api/rooms` — Create a new room: `{"name": "string"}`.
* `GET /api/rooms/{id}` — Get room details (user must be member or owner).
* `PUT /api/rooms/{id}` — Rename room (owner only): `{"name": "string"}`.
* `POST /api/rooms/join` — Join room by invitation code: `{"invitationCode": "string"}`.
* `POST /api/rooms/{id}/reopen` — Owner opens/re-enters room; re-adds owner to members.
* `POST /api/rooms/{id}/close` — Owner closes room to prevent changes (`closed = true`).
* `POST /api/rooms/{id}/open` — Owner reopens room (`closed = false`).
* `DELETE /api/rooms/{id}/leave` — Member leaves room.
* `DELETE /api/rooms/{id}/members/{memberId}` — Owner removes member.
* `DELETE /api/rooms/{id}` — Owner permanently deletes room (owner only). Returns 200 with empty body.

### Room Response (`RoomResponse`)
```json
{
  "id": "3ddff759-1668-4d92-939b-a05698d5b9f8",
  "name": "Design Sprint",
  "invitationCode": "ABC12345",
  "owner": {
    "id": 1,
    "username": "ilyes",
    "email": null
  },
  "createdAt": "2026-11-20T10:00:00Z",
  "members": [
    {
      "id": 1,
      "username": "ilyes",
      "email": null
    },
    {
      "id": 2,
      "username": "sarah",
      "email": null
    }
  ],
  "memberCount": 2,
  "member": true,
  "closed": false,
  "offline": false
}
```
*Note on Privacy:* `owner.email` and `members[].email` are always returned as `null` in `RoomResponse`.

---

## 3. Presence

### `POST /api/rooms/{roomId}/presence`
Sends client presence heartbeat. Must be called every 10 seconds while connected to a room.
* **Request:**
```json
{
  "clientId": "client-uuid-or-session-id"
}
```
* **Response:** 200 OK (empty body).

### `POST /api/rooms/{roomId}/presence/leave`
Signals that the client is leaving the room.
* **Request:**
```json
{
  "clientId": "client-uuid-or-session-id"
}
```
* **Response:** 200 OK (empty body).

### Presence Rules & TTL:
* **`HEARTBEAT_EXPIRY = 60s`**: If the room owner sends no heartbeat for 60 seconds, the backend marks `offline = true`.
* **`LEAVE_GRACE = 10s`**: Grace period after explicit leave before completely clearing session.
* **`MAX_CLIENTS_PER_USER = 10`**: Maximum client IDs per user in a single room.

---

## 4. Categories

### Endpoints
* `GET /api/rooms/{roomId}/categories` — List room categories.
* `POST /api/rooms/{roomId}/categories` — Create category: `{"title": "string"}` (201 Created).
* `PUT /api/categories/{id}` — Rename category: `{"title": "string"}` (200 OK).
* `DELETE /api/categories/{id}` — Delete category (204 No Content).
* Title max length is **40 characters**. Managed by room owner only in UI.

### Category Response (`CategoryResponse`)
```json
{
  "id": 10,
  "title": "Architecture"
}
```

---

## 5. Ideas

### Endpoints
* `GET /api/rooms/{roomId}/ideas?categoryId={id}&search={text}&sortBy=createdAt|voteCount` — List ideas.
* `POST /api/rooms/{roomId}/ideas` — Create idea.
* `PUT /api/ideas/{ideaId}` — Update idea content or position.
* `DELETE /api/ideas/{ideaId}` — Delete idea (author or room owner; returns 200 OK).
* `POST /api/ideas/{ideaId}/vote` — Upvote idea (returns updated `IdeaResponse`).
* `DELETE /api/ideas/{ideaId}/vote` — Remove vote (returns updated `IdeaResponse`).

### Idea Request (`IdeaRequest`)
```json
{
  "title": "Unified State Flow",
  "description": "Keep local and remote state synchronized using StateFlow",
  "text": "Unified State Flow",
  "x": 250.0,
  "y": 180.0,
  "color": "#273b3b",
  "categoryId": 10
}
```

### Idea Response (`IdeaResponse`)
```json
{
  "id": "8fa7b2c1-2345-4a67-8901-bcdef0123456",
  "room": {
    "id": "3ddff759-1668-4d92-939b-a05698d5b9f8",
    "name": "Design Sprint",
    "invitationCode": "ABC12345",
    "owner": { "id": 1, "username": "ilyes", "email": null },
    "createdAt": "2026-11-20T10:00:00Z",
    "members": [{ "id": 1, "username": "ilyes", "email": null }],
    "memberCount": 1,
    "member": true,
    "closed": false,
    "offline": false
  },
  "author": {
    "id": 1,
    "username": "ilyes",
    "email": "ilyes@example.com"
  },
  "text": "Unified State Flow",
  "title": "Unified State Flow",
  "description": "Keep local and remote state synchronized using StateFlow",
  "x": 250.0,
  "y": 180.0,
  "color": "#273b3b",
  "createdAt": "2026-11-20T10:15:00Z",
  "updatedAt": "2026-11-20T10:15:00Z",
  "voteCount": 3,
  "voted": true,
  "category": {
    "id": 10,
    "title": "Architecture"
  }
}
```

### Idea Coordinates & Guard Rules
* Card `(x, y)` is the **card centre** (CSS `transform: translate(-50%, -50%)`).
* Cards are fixed size: `210 x 110` world units.
* Clamped bounds: `x: 30..970`, `y: 20..480` (integers).
* `IdeaPositionGuard`:
  * Holds pending drops with an incrementing `seq` and `status: 'saving'`.
  * On save success: records confirmed coordinates, sets `clearAfterPollGen = currentPollGen + 1`.
  * Stale poll responses received before `clearAfterPollGen` do not revert the user's drop.

---

## 6. Comments

### Endpoints
* `GET /api/ideas/{ideaId}/comments` — List comments for an idea.
* `POST /api/ideas/{ideaId}/comments` — Add comment: `{"content": "string", "parentId": null}`.
* `POST /api/comments/{commentId}/replies` — Reply to comment: `{"content": "string"}`.
* `PUT /api/comments/{commentId}` — Edit comment content: `{"content": "string"}`.
* `DELETE /api/comments/{commentId}` — Delete comment (author only; returns 200 OK).
* Max comment content length: **2000 characters**.

### Comment Response (`CommentResponse`)
```json
{
  "id": "c1111111-2222-3333-4444-555555555555",
  "ideaId": "8fa7b2c1-2345-4a67-8901-bcdef0123456",
  "author": {
    "id": 2,
    "username": "sarah",
    "email": "sarah@example.com"
  },
  "content": "Great idea, we should prototype this first.",
  "text": "Great idea, we should prototype this first.",
  "parentId": null,
  "createdAt": "2026-11-20T10:30:00Z",
  "updatedAt": "2026-11-20T10:30:00Z",
  "replies": [
    {
      "id": "c2222222-3333-4444-5555-666666666666",
      "ideaId": "8fa7b2c1-2345-4a67-8901-bcdef0123456",
      "author": {
        "id": 1,
        "username": "ilyes",
        "email": "ilyes@example.com"
      },
      "content": "Agreed, let us start today.",
      "text": "Agreed, let us start today.",
      "parentId": "c1111111-2222-3333-4444-555555555555",
      "createdAt": "2026-11-20T10:35:00Z",
      "updatedAt": "2026-11-20T10:35:00Z",
      "replies": []
    }
  ]
}
```

---

## 7. Drawings

### Endpoints
* `GET /api/rooms/{roomId}/drawings` — List drawings.
* `POST /api/rooms/{roomId}/drawings` — Create drawing (201 Created).
* `PUT /api/rooms/{roomId}/drawings/{drawingId}` — Move drawing: `{"offsetX": 10.0, "offsetY": 5.0}` (200 OK).
* `DELETE /api/rooms/{roomId}/drawings/{drawingId}` — Delete drawing (204 No Content; author or room owner).

### Drawing Request & Response (`DrawingResponse`)
```json
{
  "id": "d1111111-2222-3333-4444-555555555555",
  "roomId": "3ddff759-1668-4d92-939b-a05698d5b9f8",
  "author": {
    "id": 1,
    "username": "ilyes",
    "email": "ilyes@example.com"
  },
  "kind": "pen",
  "color": "#273b3b",
  "width": 5.0,
  "opacity": 1.0,
  "payload": "[{\"x\":100.0,\"y\":120.0},{\"x\":105.0,\"y\":125.0},{\"x\":115.0,\"y\":130.0}]",
  "offsetX": 0.0,
  "offsetY": 0.0,
  "createdAt": "2026-11-20T10:20:00Z",
  "updatedAt": "2026-11-20T10:20:00Z"
}
```

### Drawing Rules, Tolerances & Offsets
* **Kinds:** `pen` (width 5, opacity 1.0), `highlighter` (width 30, opacity 0.35), `rectangle`, `circle`, `triangle` (min size 12).
* **Offsets:** Drawing `offsetX` and `offsetY` are **not clamped**.
* **Hit-Testing:**
  * Screen pixel tolerance (default `6px`) converts to world coordinates via: `screenPixels * (1000 / surfaceWidth)`.
  * Effective tolerance for strokes = `max(tolerance, strokeWidth / 2 + tolerance)`.
  * Point simplification: radial distance threshold `tolerance = 1.5` canvas units.

---

## 8. WebSocket Envelopes

### Connection
* URL: `ws://<host>:8080/ws/rooms/{roomId}?token=<jwt>`
* On open, client sends auth message: `{"type": "auth", "token": "<jwt>"}`
* Server returns: `{"type": "auth.success", "userId": 1, "name": "ilyes"}`
* Heartbeat: Server sends `{"type": "ping"}`, client responds with `{"type": "pong"}`.
* Rate limits: `cursor-move` (25/s), `card-move` (15/s), `drawing-move` (15/s), `stroke-points` (20/s).

### Client-to-Server Messages
| Type | Payload Schema |
|---|---|
| `auth` | `{"type": "auth", "token": "<jwt>"}` |
| `pong` | `{"type": "pong"}` |
| `cursor-move` | `{"type": "cursor-move", "x": 100.0, "y": 150.0}` |
| `cursor-leave` | `{"type": "cursor-leave"}` |
| `card-move` | `{"type": "card-move", "id": "uuid", "x": 200.0, "y": 250.0}` |
| `stroke-points` | `{"type": "stroke-points", "strokeId": "id", "color": "#273b3b", "width": 5.0, "opacity": 1.0, "points": [[x,y], ...]}` |
| `drawing-move` | `{"type": "drawing-move", "id": "uuid", "offsetX": 10.0, "offsetY": 5.0}` |
| `object-unlock` | `{"type": "object-unlock", "objectType": "card"\|"drawing", "id": "uuid"}` |

### Server-to-Client Messages
| Type | Example Payload |
|---|---|
| `auth.success` | `{"type": "auth.success", "userId": 1, "name": "ilyes"}` |
| `presence` | `{"type": "presence", "participants": [{"userId": 1, "name": "ilyes"}]}` |
| `ping` | `{"type": "ping"}` |
| `cursor-move` | `{"type": "cursor-move", "userId": 2, "name": "sarah", "color": "#ec4899", "x": 100.0, "y": 150.0}` |
| `cursor-leave` | `{"type": "cursor-leave", "userId": 2}` |
| `card-move` | `{"type": "card-move", "id": "uuid", "x": 200.0, "y": 250.0, "by": 1}` |
| `stroke-points` | `{"type": "stroke-points", "strokeId": "str-1", "userId": 2, "color": "#273b3b", "width": 5.0, "opacity": 1.0, "points": [[x,y]]}` |
| `drawing-move` | `{"type": "drawing-move", "id": "uuid", "offsetX": 10.0, "offsetY": 5.0, "by": 2}` |
| `object-locked` | `{"type": "object-locked", "objectType": "card", "id": "uuid", "userId": 2, "name": "sarah"}` |
| `object-unlocked` | `{"type": "object-unlocked", "objectType": "card", "id": "uuid"}` |
| `idea-created` | `{"type": "idea-created", "version": 1, "idea": <IdeaResponse>}` |
| `idea-updated` | `{"type": "idea-updated", "version": 2, "idea": <IdeaResponse>}` |
| `idea-deleted` | `{"type": "idea-deleted", "version": 3, "id": "uuid"}` |
| `drawing-created` | `{"type": "drawing-created", "version": 1, "drawing": <DrawingResponse>}` |
| `drawing-updated` | `{"type": "drawing-updated", "version": 2, "drawing": <DrawingResponse>}` |
| `drawing-deleted` | `{"type": "drawing-deleted", "version": 3, "id": "uuid"}` |
| `comment-created` | `{"type": "comment-created", "version": 1, "ideaId": "uuid", "comment": <CommentResponse>}` |
| `comment-updated` | `{"type": "comment-updated", "version": 2, "ideaId": "uuid", "comment": <CommentResponse>}` |
| `comment-deleted` | `{"type": "comment-deleted", "version": 3, "ideaId": "uuid", "commentId": "uuid"}` |
| `category-created` | `{"type": "category-created", "version": 1, "category": {"id": 10, "title": "Architecture"}}` |
| `category-updated` | `{"type": "category-updated", "version": 2, "category": {"id": 10, "title": "Architecture & Infra"}}` |
| `category-deleted` | `{"type": "category-deleted", "version": 3, "id": 10}` |

### Close Status Codes
* `4400`: Invalid / missing room ID.
* `4401`: Token missing, expired, or invalid; or auth handshake timeout (5s).
* `4403`: Forbidden (user is not an authorized member of this room).
* `4404`: Room not found.
* `4408`: Session idle timeout (30s) or ping response missing.

---

## 9. Read-Only Rules

* A room is read-only when `room.closed == true || room.offline == true`.
* `offline == true` means the owner previously joined and had presence, but their presence heartbeat has expired (> 60s) or they sent an explicit leave.
* When read-only:
  * All REST mutations return `403 Forbidden`.
  * The canvas UI disables all mutating gestures (draw, move, delete, vote, add idea).
  * Only pan/zoom (`Hand` tool) remains active.
