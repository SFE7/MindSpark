import { Injectable, OnDestroy, inject, signal } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { KeycloakAuth } from '../../../core/auth/keycloak-auth';
import { environment } from '../../../../environments/environment';
import { Idea } from '../models/idea.model';
import { CommentItem } from '../models/comment.model';
import { Category } from '../models/category.model';

export interface CardMovePayload {
  id: string;
  x: number;
  y: number;
  by: number | string;
}

export interface OnlineParticipant {
  userId: number | string;
  name: string;
}

export interface IdeaEventPayload {
  type: 'idea-created' | 'idea-updated' | 'idea-deleted';
  version: number;
  idea?: Idea;
  id?: string;
}

export interface CommentEventPayload {
  type: 'comment-created' | 'comment-updated' | 'comment-deleted';
  ideaId: string;
  commentId?: string;
  comment?: CommentItem;
  version: number;
}

export interface CursorMovePayload {
  userId: number | string;
  name: string;
  color: string;
  x: number;
  y: number;
}

export interface CursorLeavePayload {
  userId: number | string;
}

export interface DrawingEventPayload {
  type: 'drawing-created' | 'drawing-updated' | 'drawing-deleted';
  version: number;
  drawing?: any;
  id?: string;
}

export interface CategoryEventPayload {
  type: 'category-created' | 'category-updated' | 'category-deleted';
  version: number;
  category?: Category;
  id?: number | string;
}

export interface DrawingMovePayload {
  id: string;
  offsetX: number;
  offsetY: number;
  by: number | string;
}

export interface StrokePointsPayload {
  strokeId: string;
  userId: number | string;
  color: string;
  width: number;
  opacity: number;
  points: any[];
}

export interface ObjectLockPayload {
  objectType: 'card' | 'drawing';
  id: string;
  userId: number | string;
  name: string;
}

export interface ObjectUnlockPayload {
  objectType: 'card' | 'drawing';
  id: string;
}

export type ConnectionStatus = 'connected' | 'reconnecting';

@Injectable({ providedIn: 'root' })
export class RealtimeService implements OnDestroy {
  private readonly auth = inject(KeycloakAuth);

  private socket?: WebSocket;
  private currentRoomId?: string;
  private currentUser?: { userId: number | string; name: string };
  private isExplicitlyClosed = false;

  private readonly cardMovesSubject = new Subject<CardMovePayload>();
  readonly cardMoves$: Observable<CardMovePayload> = this.cardMovesSubject.asObservable();

  private readonly reconnectedSubject = new Subject<void>();
  readonly reconnected$: Observable<void> = this.reconnectedSubject.asObservable();

  private readonly ideaEventsSubject = new Subject<IdeaEventPayload>();
  readonly ideaEvents$: Observable<IdeaEventPayload> = this.ideaEventsSubject.asObservable();

  private readonly commentEventsSubject = new Subject<CommentEventPayload>();
  readonly commentEvents$: Observable<CommentEventPayload> = this.commentEventsSubject.asObservable();

  private readonly cursorMovesSubject = new Subject<CursorMovePayload>();
  readonly cursorMoves$: Observable<CursorMovePayload> = this.cursorMovesSubject.asObservable();

  private readonly cursorLeavesSubject = new Subject<CursorLeavePayload>();
  readonly cursorLeaves$: Observable<CursorLeavePayload> = this.cursorLeavesSubject.asObservable();

  private readonly drawingEventsSubject = new Subject<DrawingEventPayload>();
  readonly drawingEvents$: Observable<DrawingEventPayload> = this.drawingEventsSubject.asObservable();

  private readonly categoryEventsSubject = new Subject<CategoryEventPayload>();
  readonly categoryEvents$: Observable<CategoryEventPayload> = this.categoryEventsSubject.asObservable();

  private readonly drawingMovesSubject = new Subject<DrawingMovePayload>();
  readonly drawingMoves$: Observable<DrawingMovePayload> = this.drawingMovesSubject.asObservable();

  private readonly strokePointsSubject = new Subject<StrokePointsPayload>();
  readonly strokePoints$: Observable<StrokePointsPayload> = this.strokePointsSubject.asObservable();

  private readonly objectLockedSubject = new Subject<ObjectLockPayload>();
  readonly objectLocked$: Observable<ObjectLockPayload> = this.objectLockedSubject.asObservable();

  private readonly objectUnlockedSubject = new Subject<ObjectUnlockPayload>();
  readonly objectUnlocked$: Observable<ObjectUnlockPayload> = this.objectUnlockedSubject.asObservable();

  private readonly onlineParticipantsSignal = signal<OnlineParticipant[]>([]);
  readonly onlineParticipants = this.onlineParticipantsSignal.asReadonly();

  private readonly connectionStatusSignal = signal<ConnectionStatus>('reconnecting');
  readonly connectionStatus = this.connectionStatusSignal.asReadonly();

  // Reconnection state
  private reconnectAttempts = 0;
  private reconnectTimer?: ReturnType<typeof setTimeout>;
  private wasPreviouslyConnected = false;

  // Throttle state for card dragging
  private lastDragSentAt = 0;
  private pendingThrottleTimer?: ReturnType<typeof setTimeout>;
  private pendingPayload?: CardMovePayload;
  private readonly THROTTLE_INTERVAL_MS = 120;

  // Throttle state for cursor moves (~20/s = 50ms)
  private lastCursorSentAt = 0;
  private pendingCursorTimer?: ReturnType<typeof setTimeout>;
  private pendingCursorPos?: { x: number; y: number };
  private readonly CURSOR_THROTTLE_INTERVAL_MS = 50;

  // Throttle state for drawing moves (~12/s = 80ms)
  private lastDrawingDragSentAt = 0;
  private pendingDrawingThrottleTimer?: ReturnType<typeof setTimeout>;
  private pendingDrawingPayload?: { id: string; offsetX: number; offsetY: number };
  private readonly DRAWING_THROTTLE_INTERVAL_MS = 80;

  // Throttle state for in-progress stroke preview (~20/s = 50ms)
  private lastStrokeSentAt = 0;
  private readonly STROKE_THROTTLE_INTERVAL_MS = 50;

  joinRoom(roomId: string, user: { userId: number | string; name: string }): void {
    if (this.currentRoomId === roomId && this.socket && this.socket.readyState === WebSocket.OPEN) {
      return;
    }

    this.leaveRoom();
    this.currentRoomId = roomId;
    this.currentUser = user;
    this.isExplicitlyClosed = false;
    this.reconnectAttempts = 0;
    this.wasPreviouslyConnected = false;
    this.connectionStatusSignal.set('reconnecting');

    void this.connect();
  }

  private async connect(): Promise<void> {
    if (this.isExplicitlyClosed || !this.currentRoomId) {
      return;
    }

    try {
      const token = await this.auth.getValidToken();
      if (!token) {
        this.scheduleReconnect();
        return;
      }

      const wsUrl = environment.wsUrl;
      const url = `${wsUrl}/ws/rooms/${encodeURIComponent(this.currentRoomId)}?token=${encodeURIComponent(token)}`;

      const socket = new WebSocket(url);
      this.socket = socket;

      socket.onopen = () => {
        if (this.socket !== socket) return;
        this.connectionStatusSignal.set('connected');
        this.reconnectAttempts = 0;

        // Send auth message in-band as well
        socket.send(JSON.stringify({
          type: 'auth',
          token,
        }));

        if (this.wasPreviouslyConnected) {
          this.reconnectedSubject.next();
        }
        this.wasPreviouslyConnected = true;
      };

      socket.onmessage = (event: MessageEvent) => {
        if (this.socket !== socket) return;
        try {
          const data = JSON.parse(event.data);
          const type = data?.type;

          if (type === 'card-move') {
            this.cardMovesSubject.next({
              id: data.id,
              x: Number(data.x),
              y: Number(data.y),
              by: data.by,
            });
          } else if (type === 'presence') {
            const raw = data.participants || [];
            const list: OnlineParticipant[] = raw.map((p: any) => ({
              userId: p.userId,
              name: p.name || `User #${p.userId}`,
            }));
            this.onlineParticipantsSignal.set(list);
          } else if (type === 'idea-created' || type === 'idea-updated') {
            this.ideaEventsSubject.next({
              type,
              version: Number(data.version),
              idea: data.idea,
            });
          } else if (type === 'idea-deleted') {
            this.ideaEventsSubject.next({
              type,
              version: Number(data.version),
              id: data.id,
            });
          } else if (type === 'comment-created' || type === 'comment-updated') {
            this.commentEventsSubject.next({
              type,
              ideaId: data.ideaId,
              comment: data.comment,
              version: Number(data.version),
            });
          } else if (type === 'comment-deleted') {
            this.commentEventsSubject.next({
              type,
              ideaId: data.ideaId,
              commentId: data.commentId,
              version: Number(data.version),
            });
          } else if (type === 'cursor-move') {
            this.cursorMovesSubject.next({
              userId: data.userId,
              name: data.name || `User #${data.userId}`,
              color: data.color || '#3b82f6',
              x: Number(data.x),
              y: Number(data.y),
            });
          } else if (type === 'cursor-leave') {
            this.cursorLeavesSubject.next({
              userId: data.userId,
            });
          } else if (type === 'drawing-created' || type === 'drawing-updated') {
            this.drawingEventsSubject.next({
              type,
              version: Number(data.version),
              drawing: data.drawing,
            });
          } else if (type === 'drawing-deleted') {
            this.drawingEventsSubject.next({
              type,
              version: Number(data.version),
              id: data.id,
            });
          } else if (type === 'category-created' || type === 'category-updated') {
            this.categoryEventsSubject.next({
              type,
              version: Number(data.version),
              category: data.category,
            });
          } else if (type === 'category-deleted') {
            this.categoryEventsSubject.next({
              type,
              version: Number(data.version),
              id: data.id,
            });
          } else if (type === 'drawing-move') {
            this.drawingMovesSubject.next({
              id: data.id,
              offsetX: Number(data.offsetX),
              offsetY: Number(data.offsetY),
              by: data.by,
            });
          } else if (type === 'stroke-points') {
            this.strokePointsSubject.next({
              strokeId: data.strokeId,
              userId: data.userId,
              color: data.color || '#273b3b',
              width: Number(data.width) || 5,
              opacity: Number(data.opacity) || 1,
              points: data.points || [],
            });
          } else if (type === 'object-locked') {
            this.objectLockedSubject.next({
              objectType: data.objectType,
              id: data.id,
              userId: data.userId,
              name: data.name,
            });
          } else if (type === 'object-unlocked') {
            this.objectUnlockedSubject.next({
              objectType: data.objectType,
              id: data.id,
            });
          } else if (type === 'ping') {
            socket.send(JSON.stringify({ type: 'pong' }));
          }
        } catch (err) {
          console.debug('Failed to parse WebSocket message', err);
        }
      };

      socket.onclose = () => {
        if (this.socket !== socket) return;
        this.connectionStatusSignal.set('reconnecting');
        this.scheduleReconnect();
      };

      socket.onerror = () => {
        if (this.socket !== socket) return;
        this.connectionStatusSignal.set('reconnecting');
        try {
          socket.close();
        } catch {}
      };

    } catch (err) {
      console.debug('Error initiating WebSocket connection', err);
      this.scheduleReconnect();
    }
  }

  private scheduleReconnect(): void {
    if (this.isExplicitlyClosed || !this.currentRoomId) {
      return;
    }

    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
    }

    this.reconnectAttempts++;
    // Exponential backoff: 1s, 2s, 4s, 8s, up to 10s maximum
    const delay = Math.min(10000, 1000 * Math.pow(1.5, this.reconnectAttempts - 1));

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = undefined;
      void this.connect();
    }, delay);
  }

  broadcastCardMove(payload: CardMovePayload, isFinal: boolean = false): void {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      return;
    }

    const sendMsg = (p: CardMovePayload) => {
      if (this.socket && this.socket.readyState === WebSocket.OPEN) {
        this.socket.send(JSON.stringify({
          type: 'card-move',
          id: p.id,
          x: p.x,
          y: p.y,
        }));
      }
    };

    if (isFinal) {
      if (this.pendingThrottleTimer) {
        clearTimeout(this.pendingThrottleTimer);
        this.pendingThrottleTimer = undefined;
        this.pendingPayload = undefined;
      }
      this.lastDragSentAt = Date.now();
      sendMsg(payload);
      return;
    }

    const now = Date.now();
    const elapsed = now - this.lastDragSentAt;

    if (elapsed >= this.THROTTLE_INTERVAL_MS) {
      if (this.pendingThrottleTimer) {
        clearTimeout(this.pendingThrottleTimer);
        this.pendingThrottleTimer = undefined;
      }
      this.lastDragSentAt = now;
      this.pendingPayload = undefined;
      sendMsg(payload);
    } else {
      this.pendingPayload = payload;
      if (!this.pendingThrottleTimer) {
        const remaining = this.THROTTLE_INTERVAL_MS - elapsed;
        this.pendingThrottleTimer = setTimeout(() => {
          this.pendingThrottleTimer = undefined;
          if (this.pendingPayload) {
            this.lastDragSentAt = Date.now();
            sendMsg(this.pendingPayload);
            this.pendingPayload = undefined;
          }
        }, remaining);
      }
    }
  }

  sendCursorMove(x: number, y: number): void {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      return;
    }

    const send = (cx: number, cy: number) => {
      if (this.socket && this.socket.readyState === WebSocket.OPEN) {
        this.socket.send(JSON.stringify({
          type: 'cursor-move',
          x: cx,
          y: cy,
        }));
      }
    };

    const now = Date.now();
    const elapsed = now - this.lastCursorSentAt;

    if (elapsed >= this.CURSOR_THROTTLE_INTERVAL_MS) {
      if (this.pendingCursorTimer) {
        clearTimeout(this.pendingCursorTimer);
        this.pendingCursorTimer = undefined;
        this.pendingCursorPos = undefined;
      }
      this.lastCursorSentAt = now;
      send(x, y);
    } else {
      this.pendingCursorPos = { x, y };
      if (!this.pendingCursorTimer) {
        const remaining = this.CURSOR_THROTTLE_INTERVAL_MS - elapsed;
        this.pendingCursorTimer = setTimeout(() => {
          this.pendingCursorTimer = undefined;
          if (this.pendingCursorPos) {
            this.lastCursorSentAt = Date.now();
            send(this.pendingCursorPos.x, this.pendingCursorPos.y);
            this.pendingCursorPos = undefined;
          }
        }, remaining);
      }
    }
  }

  sendCursorLeave(): void {
    if (this.pendingCursorTimer) {
      clearTimeout(this.pendingCursorTimer);
      this.pendingCursorTimer = undefined;
      this.pendingCursorPos = undefined;
    }
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({ type: 'cursor-leave' }));
    }
  }

  broadcastDrawingMove(id: string, offsetX: number, offsetY: number, isFinal: boolean = false): void {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      return;
    }

    const sendMsg = (dId: string, ox: number, oy: number) => {
      if (this.socket && this.socket.readyState === WebSocket.OPEN) {
        this.socket.send(JSON.stringify({
          type: 'drawing-move',
          id: dId,
          offsetX: ox,
          offsetY: oy,
        }));
      }
    };

    if (isFinal) {
      if (this.pendingDrawingThrottleTimer) {
        clearTimeout(this.pendingDrawingThrottleTimer);
        this.pendingDrawingThrottleTimer = undefined;
        this.pendingDrawingPayload = undefined;
      }
      this.lastDrawingDragSentAt = Date.now();
      sendMsg(id, offsetX, offsetY);
      return;
    }

    const now = Date.now();
    const elapsed = now - this.lastDrawingDragSentAt;

    if (elapsed >= this.DRAWING_THROTTLE_INTERVAL_MS) {
      if (this.pendingDrawingThrottleTimer) {
        clearTimeout(this.pendingDrawingThrottleTimer);
        this.pendingDrawingThrottleTimer = undefined;
      }
      this.lastDrawingDragSentAt = now;
      this.pendingDrawingPayload = undefined;
      sendMsg(id, offsetX, offsetY);
    } else {
      this.pendingDrawingPayload = { id, offsetX, offsetY };
      if (!this.pendingDrawingThrottleTimer) {
        const remaining = this.DRAWING_THROTTLE_INTERVAL_MS - elapsed;
        this.pendingDrawingThrottleTimer = setTimeout(() => {
          this.pendingDrawingThrottleTimer = undefined;
          if (this.pendingDrawingPayload) {
            this.lastDrawingDragSentAt = Date.now();
            sendMsg(this.pendingDrawingPayload.id, this.pendingDrawingPayload.offsetX, this.pendingDrawingPayload.offsetY);
            this.pendingDrawingPayload = undefined;
          }
        }, remaining);
      }
    }
  }

  sendStrokePoints(strokeId: string, color: string, width: number, opacity: number, points: any[]): void {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      return;
    }
    const now = Date.now();
    if (now - this.lastStrokeSentAt >= this.STROKE_THROTTLE_INTERVAL_MS) {
      this.lastStrokeSentAt = now;
      this.socket.send(JSON.stringify({
        type: 'stroke-points',
        strokeId,
        color,
        width,
        opacity,
        points,
      }));
    }
  }

  sendObjectUnlock(objectType: 'card' | 'drawing', id: string): void {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      return;
    }
    this.socket.send(JSON.stringify({
      type: 'object-unlock',
      objectType,
      id,
    }));
  }

  leaveRoom(): void {
    this.isExplicitlyClosed = true;

    if (this.pendingThrottleTimer) {
      clearTimeout(this.pendingThrottleTimer);
      this.pendingThrottleTimer = undefined;
      this.pendingPayload = undefined;
    }

    if (this.pendingCursorTimer) {
      clearTimeout(this.pendingCursorTimer);
      this.pendingCursorTimer = undefined;
      this.pendingCursorPos = undefined;
    }

    if (this.pendingDrawingThrottleTimer) {
      clearTimeout(this.pendingDrawingThrottleTimer);
      this.pendingDrawingThrottleTimer = undefined;
      this.pendingDrawingPayload = undefined;
    }

    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = undefined;
    }

    if (this.socket) {
      try {
        this.socket.close();
      } catch {}
      this.socket = undefined;
    }

    this.currentRoomId = undefined;
    this.currentUser = undefined;
    this.onlineParticipantsSignal.set([]);
    this.connectionStatusSignal.set('reconnecting');
    this.wasPreviouslyConnected = false;
  }

  ngOnDestroy(): void {
    this.leaveRoom();
  }
}
