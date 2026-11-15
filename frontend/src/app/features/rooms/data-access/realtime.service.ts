import { Injectable, OnDestroy, signal } from '@angular/core';
import { createClient, RealtimeChannel, SupabaseClient } from '@supabase/supabase-js';
import { Observable, Subject } from 'rxjs';
import { environment } from '../../../../environments/environment';

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

export type ConnectionStatus = 'connected' | 'reconnecting';

@Injectable({ providedIn: 'root' })
export class RealtimeService implements OnDestroy {
  // NOTE: Channels are public for now without Supabase auth; RLS / auth integration planned for a later task.
  private readonly supabase: SupabaseClient;
  private channel?: RealtimeChannel;
  private currentRoomId?: string;

  private readonly cardMovesSubject = new Subject<CardMovePayload>();
  readonly cardMoves$: Observable<CardMovePayload> = this.cardMovesSubject.asObservable();

  private readonly onlineParticipantsSignal = signal<OnlineParticipant[]>([]);
  readonly onlineParticipants = this.onlineParticipantsSignal.asReadonly();

  private readonly connectionStatusSignal = signal<ConnectionStatus>('reconnecting');
  readonly connectionStatus = this.connectionStatusSignal.asReadonly();

  // Throttle state for card dragging
  private lastDragSentAt = 0;
  private pendingThrottleTimer?: ReturnType<typeof setTimeout>;
  private pendingPayload?: CardMovePayload;
  private readonly THROTTLE_INTERVAL_MS = 120;

  constructor() {
    this.supabase = createClient(environment.supabase.url, environment.supabase.publishableKey, {
      auth: {
        persistSession: false,
        autoRefreshToken: false,
      },
      realtime: {
        params: {
          eventsPerSecond: 20,
        },
      },
    });
  }

  joinRoom(roomId: string, user: { userId: number | string; name: string }): void {
    if (this.currentRoomId === roomId && this.channel) {
      return;
    }

    this.leaveRoom();
    this.currentRoomId = roomId;
    this.connectionStatusSignal.set('reconnecting');

    const channelName = `room:${roomId}`;
    const channel = this.supabase.channel(channelName, {
      config: {
        broadcast: { self: false },
        presence: { key: String(user.userId) },
      },
    });

    channel
      .on('broadcast', { event: 'card-move' }, ({ payload }) => {
        if (payload && payload.id) {
          this.cardMovesSubject.next(payload as CardMovePayload);
        }
      })
      .on('presence', { event: 'sync' }, () => {
        this.updatePresenceState(channel);
      })
      .subscribe(async status => {
        if (status === 'SUBSCRIBED') {
          this.connectionStatusSignal.set('connected');
          try {
            await channel.track({
              userId: user.userId,
              name: user.name,
            });
          } catch (err) {
            console.error('Error tracking Supabase presence:', err);
          }
        } else if (status === 'TIMED_OUT' || status === 'CLOSED' || status === 'CHANNEL_ERROR') {
          this.connectionStatusSignal.set('reconnecting');
        }
      });

    this.channel = channel;
  }

  broadcastCardMove(payload: CardMovePayload, isFinal: boolean = false): void {
    if (!this.channel) return;

    if (isFinal) {
      if (this.pendingThrottleTimer) {
        clearTimeout(this.pendingThrottleTimer);
        this.pendingThrottleTimer = undefined;
        this.pendingPayload = undefined;
      }
      this.lastDragSentAt = Date.now();
      void this.channel.send({
        type: 'broadcast',
        event: 'card-move',
        payload,
      });
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
      void this.channel.send({
        type: 'broadcast',
        event: 'card-move',
        payload,
      });
    } else {
      this.pendingPayload = payload;
      if (!this.pendingThrottleTimer) {
        const remaining = this.THROTTLE_INTERVAL_MS - elapsed;
        this.pendingThrottleTimer = setTimeout(() => {
          this.pendingThrottleTimer = undefined;
          if (this.pendingPayload && this.channel) {
            this.lastDragSentAt = Date.now();
            void this.channel.send({
              type: 'broadcast',
              event: 'card-move',
              payload: this.pendingPayload,
            });
            this.pendingPayload = undefined;
          }
        }, remaining);
      }
    }
  }

  leaveRoom(): void {
    if (this.pendingThrottleTimer) {
      clearTimeout(this.pendingThrottleTimer);
      this.pendingThrottleTimer = undefined;
      this.pendingPayload = undefined;
    }

    if (this.channel) {
      void this.supabase.removeChannel(this.channel);
      this.channel = undefined;
    }

    this.currentRoomId = undefined;
    this.onlineParticipantsSignal.set([]);
    this.connectionStatusSignal.set('reconnecting');
  }

  private updatePresenceState(channel: RealtimeChannel): void {
    const rawState = channel.presenceState();
    const participantsMap = new Map<number | string, OnlineParticipant>();

    for (const key of Object.keys(rawState)) {
      const presences = rawState[key] as any[];
      for (const p of presences) {
        if (p.userId != null) {
          participantsMap.set(p.userId, {
            userId: p.userId,
            name: p.name || `User #${p.userId}`,
          });
        }
      }
    }

    this.onlineParticipantsSignal.set(Array.from(participantsMap.values()));
  }

  ngOnDestroy(): void {
    this.leaveRoom();
  }
}
