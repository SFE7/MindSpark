import { Subscription } from 'rxjs';
import { RoomsService } from '../../data-access/rooms.service';
import { KeycloakAuth } from '../../../../core/auth/keycloak-auth';

export interface PresenceHelperOptions {
  roomId: string;
  roomsService: RoomsService;
  auth: KeycloakAuth;
  onUserRemoved: () => void;
  heartbeatIntervalMs?: number;
  clientId?: string;
}

export class PresenceHelper {
  private readonly roomId: string;
  private readonly roomsService: RoomsService;
  private readonly auth: KeycloakAuth;
  private readonly onUserRemoved: () => void;
  private readonly heartbeatIntervalMs: number;
  readonly clientId: string;

  private heartbeatTimer?: ReturnType<typeof setInterval>;
  private heartbeatSub?: Subscription;
  private isDestroyed = false;

  constructor(options: PresenceHelperOptions) {
    this.roomId = options.roomId;
    this.roomsService = options.roomsService;
    this.auth = options.auth;
    this.onUserRemoved = options.onUserRemoved;
    this.heartbeatIntervalMs = options.heartbeatIntervalMs ?? 10000;
    this.clientId = options.clientId || this.resolveClientId();

    this.init();
  }

  private resolveClientId(): string {
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

  private init(): void {
    if (typeof window !== 'undefined') {
      document.addEventListener('visibilitychange', this.handleVisibilityChange);
      window.addEventListener('pagehide', this.handlePageHide);
    }

    // Send immediate heartbeat on load before user can act
    this.heartbeat();

    // Schedule regular heartbeat every 10s
    this.heartbeatTimer = setInterval(() => {
      this.heartbeat();
    }, this.heartbeatIntervalMs);
  }

  private handleVisibilityChange = (): void => {
    if (typeof document !== 'undefined' && document.visibilityState === 'visible') {
      this.heartbeat();
    }
  };

  private handlePageHide = (): void => {
    this.sendKeepaliveLeave();
  };

  heartbeat(): void {
    if (this.isDestroyed || !this.roomId) return;

    this.heartbeatSub?.unsubscribe();
    this.heartbeatSub = this.roomsService.sendHeartbeat(this.roomId, this.clientId).subscribe({
      next: () => {},
      error: (err: unknown) => {
        if (this.isDestroyed) return;
        if (err && typeof err === 'object' && 'status' in err && (err as { status: number }).status === 403) {
          this.onUserRemoved();
        }
      },
    });
  }

  sendKeepaliveLeave(): void {
    if (!this.roomId || !this.clientId) return;

    const token = this.auth.currentToken;
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    };
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    const body = JSON.stringify({ clientId: this.clientId });

    if (typeof fetch === 'function') {
      try {
        fetch(`/api/rooms/${encodeURIComponent(this.roomId)}/presence/leave`, {
          method: 'POST',
          headers,
          body,
          keepalive: true,
        }).catch(() => {
          // Ignore network errors during page unload
        });
      } catch {
        // Ignore synchronous fetch setup errors on unload
      }
    }
  }

  destroy(): void {
    if (this.isDestroyed) return;
    this.isDestroyed = true;

    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = undefined;
    }

    this.heartbeatSub?.unsubscribe();
    this.heartbeatSub = undefined;

    if (typeof window !== 'undefined') {
      document.removeEventListener('visibilitychange', this.handleVisibilityChange);
      window.removeEventListener('pagehide', this.handlePageHide);
    }

    this.sendKeepaliveLeave();
  }
}
