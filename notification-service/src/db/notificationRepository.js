import { DatabaseSync } from 'node:sqlite';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

export class NotificationRepository {
  constructor(dbPath) {
    if (dbPath && dbPath !== ':memory:') {
      const dir = path.dirname(dbPath);
      if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
      }
    }
    this.db = new DatabaseSync(dbPath || ':memory:');
    this.initSchema();
  }

  initSchema() {
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS notifications (
        id TEXT PRIMARY KEY,
        event_id TEXT NOT NULL,
        user_id TEXT NOT NULL,
        event_type TEXT NOT NULL,
        title TEXT NOT NULL,
        message TEXT NOT NULL,
        action_url TEXT,
        metadata_json TEXT,
        is_read INTEGER NOT NULL DEFAULT 0,
        created_at TEXT NOT NULL,
        UNIQUE(event_id, user_id)
      );
      CREATE INDEX IF NOT EXISTS idx_notifications_user_created ON notifications(user_id, created_at DESC);
    `);
  }

  insertNotification({ id, eventId, userId, eventType, title, message, actionUrl, metadata, createdAt }) {
    const notifId = id || crypto.randomUUID();
    const created = createdAt || new Date().toISOString();
    const metaStr = metadata ? JSON.stringify(metadata) : null;

    const stmt = this.db.prepare(`
      INSERT OR IGNORE INTO notifications (
        id, event_id, user_id, event_type, title, message, action_url, metadata_json, is_read, created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
    `);

    const result = stmt.run(
      notifId,
      eventId,
      userId,
      eventType || 'NOTIFICATION',
      title || 'Notification',
      message || '',
      actionUrl || null,
      metaStr,
      created
    );

    if (result.changes > 0) {
      return this.findByIdAndUser(notifId, userId);
    }
    return null;
  }

  listNotifications(userId, { limit = 30, unreadOnly = false } = {}) {
    const clampedLimit = Math.max(1, Math.min(100, Number(limit) || 30));
    let query = `
      SELECT id, event_id, user_id, event_type, title, message, action_url, metadata_json, is_read, created_at
      FROM notifications
      WHERE user_id = ?
    `;
    const params = [userId];

    if (unreadOnly) {
      query += ' AND is_read = 0';
    }
    query += ' ORDER BY created_at DESC LIMIT ?';
    params.push(clampedLimit);

    const stmt = this.db.prepare(query);
    const rows = stmt.all(...params);
    const unreadCount = this.countUnread(userId);

    const items = rows.map(r => this.mapRow(r));
    return { items, unreadCount };
  }

  countUnread(userId) {
    const stmt = this.db.prepare('SELECT COUNT(*) as cnt FROM notifications WHERE user_id = ? AND is_read = 0');
    const row = stmt.get(userId);
    return row ? Number(row.cnt) : 0;
  }

  findByIdAndUser(id, userId) {
    const stmt = this.db.prepare(`
      SELECT id, event_id, user_id, event_type, title, message, action_url, metadata_json, is_read, created_at
      FROM notifications
      WHERE id = ? AND user_id = ?
    `);
    const r = stmt.get(id, userId);
    return r ? this.mapRow(r) : null;
  }

  markAsRead(id, userId) {
    const stmt = this.db.prepare('UPDATE notifications SET is_read = 1 WHERE id = ? AND user_id = ?');
    const result = stmt.run(id, userId);
    if (result.changes === 0) {
      return null;
    }
    return this.findByIdAndUser(id, userId);
  }

  markAllRead(userId) {
    const stmt = this.db.prepare('UPDATE notifications SET is_read = 1 WHERE user_id = ? AND is_read = 0');
    const result = stmt.run(userId);
    return { updatedCount: Number(result.changes) };
  }

  deleteNotification(id, userId) {
    const stmt = this.db.prepare('DELETE FROM notifications WHERE id = ? AND user_id = ?');
    const result = stmt.run(id, userId);
    return result.changes > 0;
  }

  mapRow(r) {
    let parsedMetadata = null;
    if (r.metadata_json) {
      try {
        parsedMetadata = JSON.parse(r.metadata_json);
      } catch (e) {
        parsedMetadata = null;
      }
    }
    return {
      id: r.id,
      eventId: r.event_id,
      userId: r.user_id,
      eventType: r.event_type,
      title: r.title,
      message: r.message,
      actionUrl: r.action_url,
      metadata: parsedMetadata,
      isRead: Boolean(r.is_read),
      createdAt: r.created_at
    };
  }

  close() {
    this.db.close();
  }
}
