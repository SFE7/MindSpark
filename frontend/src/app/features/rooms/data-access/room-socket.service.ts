import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { KeycloakAuth } from '../../../core/auth/keycloak-auth';

export interface WsSharedUser {
  id: number;
  username: string;
  email: string;
}

export interface WsSharedRoom {
  id: string;
  name: string;
  invitationCode: string;
  owner: WsSharedUser;
  members: WsSharedUser[];
  memberCount: number;
  closed: boolean;
  offline: boolean;
}

export interface WsSharedIdea {
  id: string;
  title: string;
  description: string;
  text: string;
  x: number | null;
  y: number | null;
  color: string;
  categoryId: number | null;
  categoryTitle: string | null;
  voteCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface WsSharedCategory {
  id: number;
  title: string;
}

export interface RoomWebSocketEvent {
  roomId: string;
  type: string;
  sequence: number;
  timestamp: string;
  room?: WsSharedRoom;
  idea?: WsSharedIdea;
  category?: WsSharedCategory;
  ideaId?: string;
  categoryId?: number;
  offline?: boolean;
}

@Injectable({ providedIn: 'root' })
export class RoomSocketService {
  private readonly auth: KeycloakAuth;

  constructor(auth?: KeycloakAuth) {
    if (auth) {
      this.auth = auth;
    } else {
      this.auth = inject(KeycloakAuth);
    }
  }

  private socket: WebSocket | null = null;
  private currentRoomId: string | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private tokenRefreshTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectDelayMs = 500;
  private isExplicitDisconnect = false;

  private readonly connectedSubject = new BehaviorSubject<boolean>(false);
  readonly connected$: Observable<boolean> = this.connectedSubject.asObservable();

  private readonly eventsSubject = new Subject<RoomWebSocketEvent>();
  readonly events$: Observable<RoomWebSocketEvent> = this.eventsSubject.asObservable();

  private readonly closedSubject = new Subject<{ code: number; reason: string }>();
  readonly closed$: Observable<{ code: number; reason: string }> = this.closedSubject.asObservable();

  // Ordering per idea (Amendment 1)
  private readonly ideaTimestamps = new Map<string, { updatedAt: number; sequence: number }>();
  private currentSequence = 0;

  get isConnected(): boolean {
    return this.connectedSubject.value;
  }

  get clientId(): string {
    const storageKey = 'mindspark_presence_client_id';
    try {
      const existing = typeof sessionStorage !== 'undefined' ? sessionStorage.getItem(storageKey) : null;
      if (existing && existing.trim().length > 0 && existing.length <= 64) {
        return existing;
      }
      const generated =
        typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
          ? crypto.randomUUID()
          : 'c-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10);
      const safeId = generated.slice(0, 64);
      if (typeof sessionStorage !== 'undefined') {
        sessionStorage.setItem(storageKey, safeId);
      }
      return safeId;
    } catch {
      return 'c-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10);
    }
  }

  connect(roomId: string): void {
    if (this.currentRoomId === roomId && this.socket && (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)) {
      return;
    }

    this.disconnect();
    this.currentRoomId = roomId;
    this.isExplicitDisconnect = false;
    this.reconnectDelayMs = 500;
    this.initSocket();
  }

  private getSocketUrl(): string {
    if (typeof window === 'undefined') {
      return 'ws://localhost:8080/ws/rooms';
    }
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}/ws/rooms`;
  }

  private initSocket(): void {
    if (typeof WebSocket === 'undefined' || !this.currentRoomId) {
      return;
    }

    try {
      this.socket = new WebSocket(this.getSocketUrl());
    } catch {
      this.scheduleReconnect();
      return;
    }

    this.socket.onopen = () => {
      void this.sendAuth();
    };

    this.socket.onmessage = (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data);
        if (data.type === 'auth.success') {
          this.connectedSubject.next(true);
          this.reconnectDelayMs = 500;
          this.currentSequence = typeof data.sequence === 'number' ? data.sequence : 0;
          this.scheduleTokenRefresh();
          return;
        }

        if (data.type) {
          this.eventsSubject.next(data as RoomWebSocketEvent);
        }
      } catch {
        // Ignored unparseable message
      }
    };

    this.socket.onclose = (event: CloseEvent) => {
      this.connectedSubject.next(false);
      this.clearTokenRefresh();
      this.closedSubject.next({ code: event.code, reason: event.reason });

      // Amendment 2: 4403 (removed/forbidden) and 4404 (deleted) must NOT reconnect
      if (event.code === 4403 || event.code === 4404) {
        return;
      }

      if (!this.isExplicitDisconnect) {
        this.scheduleReconnect();
      }
    };

    this.socket.onerror = () => {
      // onclose will follow
    };
  }

  private async sendAuth(): Promise<void> {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN || !this.currentRoomId) {
      return;
    }

    const token = await this.auth.getValidToken();
    if (!token) {
      this.socket.close(4401, 'No token available');
      return;
    }

    const payload = JSON.stringify({
      type: 'auth',
      token,
      roomId: this.currentRoomId,
      clientId: this.clientId,
    });
    this.socket.send(payload);
  }

  private scheduleTokenRefresh(): void {
    this.clearTokenRefresh();
    this.tokenRefreshTimer = setTimeout(async () => {
      if (this.socket && this.socket.readyState === WebSocket.OPEN) {
        try {
          const token = await this.auth.getValidToken();
          if (token && this.socket && this.socket.readyState === WebSocket.OPEN) {
            this.socket.send(JSON.stringify({ type: 'auth', token }));
            this.scheduleTokenRefresh();
          }
        } catch {
          this.scheduleTokenRefresh();
        }
      }
    }, 45000);
  }

  private clearTokenRefresh(): void {
    if (this.tokenRefreshTimer) {
      clearTimeout(this.tokenRefreshTimer);
      this.tokenRefreshTimer = null;
    }
  }

  private scheduleReconnect(): void {
    if (this.isExplicitDisconnect || this.reconnectTimer) {
      return;
    }

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      if (!this.isExplicitDisconnect && this.currentRoomId) {
        this.initSocket();
        this.reconnectDelayMs = Math.min(5000, this.reconnectDelayMs * 2);
      }
    }, this.reconnectDelayMs);
  }

  // Ordering management (Amendment 1)
  isStaleIdeaEvent(ideaId: string, updatedAtStr: string, sequence: number): boolean {
    const eventTime = new Date(updatedAtStr).getTime();
    const last = this.ideaTimestamps.get(ideaId);
    if (!last) return false;

    if (eventTime < last.updatedAt) {
      return true;
    }
    if (eventTime === last.updatedAt && sequence <= last.sequence) {
      return true;
    }
    return false;
  }

  recordIdeaEvent(ideaId: string, updatedAtStr: string, sequence: number): void {
    const eventTime = new Date(updatedAtStr).getTime();
    this.ideaTimestamps.set(ideaId, { updatedAt: eventTime, sequence });
    if (sequence > this.currentSequence) {
      this.currentSequence = sequence;
    }
  }

  resetSequence(): void {
    this.ideaTimestamps.clear();
    this.currentSequence = 0;
  }

  disconnect(): void {
    this.isExplicitDisconnect = true;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.clearTokenRefresh();
    if (this.socket) {
      try {
        this.socket.close(1000, 'Normal closure');
      } catch {
        // ignore
      }
      this.socket = null;
    }
    this.connectedSubject.next(false);
    this.resetSequence();
  }
}
