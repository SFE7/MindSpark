import { Injectable, inject, signal, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { NotificationItem, NotificationListResponse, UnreadCountResponse, ReadAllResponse } from './notification.models';
import { KeycloakAuth } from '../auth/keycloak-auth';

@Injectable({
  providedIn: 'root'
})
export class NotificationService implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(KeycloakAuth);

  readonly notifications = signal<NotificationItem[]>([]);
  readonly unreadCount = signal<number>(0);
  readonly loading = signal<boolean>(false);

  private pollIntervalId: ReturnType<typeof setInterval> | null = null;

  loadNotifications(limit = 30, unreadOnly = false): void {
    if (!this.auth.isLoggedIn) {
      return;
    }

    this.loading.set(true);
    this.http.get<NotificationListResponse>(`/api/notifications?limit=${limit}&unreadOnly=${unreadOnly}`)
      .subscribe({
        next: (res) => {
          this.notifications.set(res.items || []);
          this.unreadCount.set(typeof res.unreadCount === 'number' ? res.unreadCount : 0);
          this.loading.set(false);
        },
        error: (err) => {
          console.warn('[NotificationService] Failed to load notifications:', err);
          this.loading.set(false);
        }
      });
  }

  refreshUnreadCount(): void {
    if (!this.auth.isLoggedIn) {
      return;
    }

    this.http.get<UnreadCountResponse>('/api/notifications/unread-count')
      .subscribe({
        next: (res) => {
          this.unreadCount.set(typeof res.unreadCount === 'number' ? res.unreadCount : 0);
        },
        error: (err) => {
          console.warn('[NotificationService] Failed to fetch unread count:', err);
        }
      });
  }

  markAsRead(id: string): void {
    if (!id) return;

    this.http.patch<NotificationItem>(`/api/notifications/${id}/read`, {})
      .subscribe({
        next: (updated) => {
          this.notifications.update(list =>
            list.map(item => (item.id === id ? { ...item, isRead: true } : item))
          );
          this.unreadCount.update(c => Math.max(0, c - 1));
        },
        error: (err) => {
          console.warn(`[NotificationService] Failed to mark ${id} as read:`, err);
        }
      });
  }

  markAllRead(): void {
    this.http.patch<ReadAllResponse>('/api/notifications/read-all', {})
      .subscribe({
        next: () => {
          this.notifications.update(list => list.map(item => ({ ...item, isRead: true })));
          this.unreadCount.set(0);
        },
        error: (err) => {
          console.warn('[NotificationService] Failed to mark all as read:', err);
        }
      });
  }

  deleteNotification(id: string): void {
    if (!id) return;

    this.http.delete<void>(`/api/notifications/${id}`)
      .subscribe({
        next: () => {
          const itemToDelete = this.notifications().find(i => i.id === id);
          if (itemToDelete && !itemToDelete.isRead) {
            this.unreadCount.update(c => Math.max(0, c - 1));
          }
          this.notifications.update(list => list.filter(i => i.id !== id));
        },
        error: (err) => {
          console.warn(`[NotificationService] Failed to delete ${id}:`, err);
        }
      });
  }

  startPolling(): void {
    this.stopPolling();
    // Refresh count on start
    this.refreshUnreadCount();
    // Poll every 30s
    this.pollIntervalId = setInterval(() => {
      this.refreshUnreadCount();
    }, 30000);
  }

  stopPolling(): void {
    if (this.pollIntervalId) {
      clearInterval(this.pollIntervalId);
      this.pollIntervalId = null;
    }
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }
}
