import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { RoomSocketService } from './room-socket.service';
import { KeycloakAuth } from '../../../core/auth/keycloak-auth';

describe('RoomSocketService', () => {
  let service: RoomSocketService;
  let mockAuth: Partial<KeycloakAuth>;

  beforeEach(() => {
    mockAuth = {
      getValidToken: vi.fn().mockResolvedValue('test-token'),
      currentToken: 'test-token',
      isLoggedIn: true,
    };

    service = new RoomSocketService(mockAuth as KeycloakAuth);
  });

  afterEach(() => {
    service.disconnect();
    vi.restoreAllMocks();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('Ordering per idea (Amendment 1)', () => {
    const ideaId = 'idea-123';
    const baseTime = '2026-11-14T12:00:00.000Z';
    const newerTime = '2026-11-14T12:00:05.000Z';
    const olderTime = '2026-11-14T11:59:55.000Z';

    it('should accept first event for an idea', () => {
      expect(service.isStaleIdeaEvent(ideaId, baseTime, 1)).toBe(false);
    });

    it('should drop event with older updatedAt', () => {
      service.recordIdeaEvent(ideaId, baseTime, 5);
      expect(service.isStaleIdeaEvent(ideaId, olderTime, 10)).toBe(true);
    });

    it('should drop event with equal updatedAt and lower or equal sequence', () => {
      service.recordIdeaEvent(ideaId, baseTime, 5);
      expect(service.isStaleIdeaEvent(ideaId, baseTime, 4)).toBe(true);
      expect(service.isStaleIdeaEvent(ideaId, baseTime, 5)).toBe(true);
    });

    it('should accept event with equal updatedAt and higher sequence (tie-break)', () => {
      service.recordIdeaEvent(ideaId, baseTime, 5);
      expect(service.isStaleIdeaEvent(ideaId, baseTime, 6)).toBe(false);
    });

    it('should accept event with newer updatedAt even if sequence is lower', () => {
      service.recordIdeaEvent(ideaId, baseTime, 10);
      expect(service.isStaleIdeaEvent(ideaId, newerTime, 1)).toBe(false);
    });

    it('should reset sequence tracking after reconnect so no events are dropped', () => {
      service.recordIdeaEvent(ideaId, baseTime, 10);
      service.resetSequence();
      // After resetSequence, older timestamp from before reconnect should not be blocked
      expect(service.isStaleIdeaEvent(ideaId, olderTime, 1)).toBe(false);
    });
  });

  describe('Client ID resolution', () => {
    it('should provide a consistent client ID', () => {
      const id1 = service.clientId;
      const id2 = service.clientId;
      expect(id1).toBeTruthy();
      expect(id1).toEqual(id2);
      expect(id1.length).toBeLessThanOrEqual(64);
    });
  });
});
