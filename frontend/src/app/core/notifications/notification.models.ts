export interface NotificationItem {
  id: string;
  eventId: string;
  userId: string;
  eventType: string;
  title: string;
  message: string;
  actionUrl?: string | null;
  metadata?: Record<string, unknown> | null;
  isRead: boolean;
  createdAt: string;
}

export interface NotificationListResponse {
  items: NotificationItem[];
  unreadCount: number;
}

export interface UnreadCountResponse {
  unreadCount: number;
}

export interface ReadAllResponse {
  updatedCount: number;
}
