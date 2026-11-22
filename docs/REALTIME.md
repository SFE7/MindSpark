# MindSpark Real-Time Collaboration Protocol

## Endpoint & Authentication
- **URL**: `ws://<host>:8080/ws/rooms/{roomId}?token=<jwt>`
- **Handshake**: Clients supply a Keycloak Bearer token in the `token` query param. The server validates the token against JWKS and confirms room membership. Tokens are never logged.

## Client-to-Server Messages
| Type | Payload Schema | Rate | Purpose |
|------|----------------|------|---------|
| `cursor-move` | `{ type: "cursor-move", x: number, y: number }` | 25/s | Normalized cursor (0..1000 x 0..500). |
| `cursor-leave` | `{ type: "cursor-leave" }` | - | Cursor exited canvas. |
| `card-move` | `{ type: "card-move", id: string, x: number, y: number }` | 15/s | Card movement. Acquires 3 s lock (`card:{id}`). |
| `stroke-points` | `{ type: "stroke-points", strokeId: string, color: string, width: number, opacity: number, points: Array<{x,y}> }` | 20/s | In-progress pen/highlighter stroke relay. |
| `drawing-move` | `{ type: "drawing-move", id: string, offsetX: number, offsetY: number }` | 15/s | Drawing drag. Acquires 3 s lock (`drawing:{id}`). |
| `object-unlock` | `{ type: "object-unlock", objectType: "card"\|"drawing", id: string }` | - | Releases soft lock on drop. |

## Server-to-Client Messages
| Type | Payload Schema | Description |
|------|----------------|-------------|
| `auth.success` | `{ type: "auth.success", userId: number, username: string }` | Handshake verified. |
| `cursor-move` | `{ type: "cursor-move", userId: number, name: string, color: string, x: number, y: number }` | Relayed cursor position. |
| `cursor-leave` | `{ type: "cursor-leave", userId: number }` | Cursor exited canvas. |
| `card-move` | `{ type: "card-move", id: string, x: number, y: number, by: string }` | Relayed card coordinates. |
| `stroke-points` | `{ type: "stroke-points", strokeId: string, userId: number, color: string, width: number, opacity: number, points: Array<{x,y}> }` | In-progress stroke preview. |
| `drawing-move` | `{ type: "drawing-move", id: string, offsetX: number, offsetY: number, by: number }` | Relayed drawing position. |
| `object-locked` | `{ type: "object-locked", objectType: "card"\|"drawing", id: string, userId: number, name: string }` | Object lock acquired. |
| `object-unlocked` | `{ type: "object-unlocked", objectType: "card"\|"drawing", id: string }` | Object lock released. |
| `idea-created` / `idea-updated` | `{ type: string, idea: IdeaResponse, version: number }` | AFTER_COMMIT broadcast. |
| `idea-deleted` | `{ type: "idea-deleted", id: string, version: number }` | AFTER_COMMIT broadcast. |
| `drawing-created` / `drawing-updated` | `{ type: string, drawing: DrawingResponse, version: number }` | AFTER_COMMIT broadcast. |
| `drawing-deleted` | `{ type: "drawing-deleted", id: string, version: number }` | AFTER_COMMIT broadcast. |
| `comment-created` / `comment-updated` | `{ type: string, ideaId: string, comment: CommentResponse }` | AFTER_COMMIT broadcast. |
| `comment-deleted` | `{ type: "comment-deleted", ideaId: string, commentId: string }` | AFTER_COMMIT broadcast. |

## Concurrency & Integrity
1. **Transactional Delivery**: Entity updates use `@TransactionalEventListener(phase = AFTER_COMMIT)` to ensure messages dispatch only after database commits succeed.
2. **Soft Locking**: Moving cards or drawings claims a 3 s sliding lock. Competing drags are blocked client-side with `"held by <User>"`. Locks auto-release on drop, disconnect, or timeout.
3. **Resurrection Safety**: Clients keep a 60 s tombstone cache for deleted items so background polling (15 s) cannot revive deleted entities.
4. **Coordinate Space**: Normalized to `0..1000` (X) and `0..500` (Y). Server clamps values on ingress.

# MindSpark Real-Time Collaboration Protocol

## Endpoint & Authentication
- **URL**: `ws://<host>:8080/ws/rooms/{roomId}?token=<jwt>`
- **Handshake**: Clients supply a Keycloak Bearer token in the `token` query param. The server validates the token against JWKS and confirms room membership. Tokens are never logged.

## Client-to-Server Messages
| Type | Payload Schema | Rate | Purpose |
|------|----------------|------|---------|
| `cursor-move` | `{ type: "cursor-move", x: number, y: number }` | 25/s | Normalized cursor (0..1000 x 0..500). |
| `cursor-leave` | `{ type: "cursor-leave" }` | - | Cursor exited canvas. |
| `card-move` | `{ type: "card-move", id: string, x: number, y: number }` | 15/s | Card movement. Acquires 3 s lock (`card:{id}`). |
| `stroke-points` | `{ type: "stroke-points", strokeId: string, color: string, width: number, opacity: number, points: Array<{x,y}> }` | 20/s | In-progress pen/highlighter stroke relay. |
| `drawing-move` | `{ type: "drawing-move", id: string, offsetX: number, offsetY: number }` | 15/s | Drawing drag. Acquires 3 s lock (`drawing:{id}`). |
| `object-unlock` | `{ type: "object-unlock", objectType: "card"\|"drawing", id: string }` | - | Releases soft lock on drop. |

## Server-to-Client Messages
| Type | Payload Schema | Description |
|------|----------------|-------------|
| `auth.success` | `{ type: "auth.success", userId: number, username: string }` | Handshake verified. |
| `cursor-move` | `{ type: "cursor-move", userId: number, name: string, color: string, x: number, y: number }` | Relayed cursor position. |
| `cursor-leave` | `{ type: "cursor-leave", userId: number }` | Cursor exited canvas. |
| `card-move` | `{ type: "card-move", id: string, x: number, y: number, by: string }` | Relayed card coordinates. |
| `stroke-points` | `{ type: "stroke-points", strokeId: string, userId: number, color: string, width: number, opacity: number, points: Array<{x,y}> }` | In-progress stroke preview. |
| `drawing-move` | `{ type: "drawing-move", id: string, offsetX: number, offsetY: number, by: number }` | Relayed drawing position. |
| `object-locked` | `{ type: "object-locked", objectType: "card"\|"drawing", id: string, userId: number, name: string }` | Object lock acquired. |
| `object-unlocked` | `{ type: "object-unlocked", objectType: "card"\|"drawing", id: string }` | Object lock released. |
| `idea-created` / `idea-updated` | `{ type: string, idea: IdeaResponse, version: number }` | AFTER_COMMIT broadcast. |
| `idea-deleted` | `{ type: "idea-deleted", id: string, version: number }` | AFTER_COMMIT broadcast. |
| `drawing-created` / `drawing-updated` | `{ type: string, drawing: DrawingResponse, version: number }` | AFTER_COMMIT broadcast. |
| `drawing-deleted` | `{ type: "drawing-deleted", id: string, version: number }` | AFTER_COMMIT broadcast. |
| `comment-created` / `comment-updated` | `{ type: string, ideaId: string, comment: CommentResponse }` | AFTER_COMMIT broadcast. |
| `comment-deleted` | `{ type: "comment-deleted", ideaId: string, commentId: string }` | AFTER_COMMIT broadcast. |

## Concurrency & Integrity
1. **Transactional Delivery**: Entity updates use `@TransactionalEventListener(phase = AFTER_COMMIT)` to ensure messages dispatch only after database commits succeed.
2. **Soft Locking**: Moving cards or drawings claims a 3 s sliding lock. Competing drags are blocked client-side with `"held by <User>"`. Locks auto-release on drop, disconnect, or timeout.
3. **Resurrection Safety**: Clients keep a 60 s tombstone cache for deleted items so background polling (15 s) cannot revive deleted entities.
4. **Coordinate Space**: Normalized to `0..1000` (X) and `0..500` (Y). Server clamps values on ingress.
