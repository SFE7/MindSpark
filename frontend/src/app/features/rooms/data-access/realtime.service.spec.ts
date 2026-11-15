import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { RealtimeService } from './realtime.service';

describe('RealtimeService', () => {
  let service: RealtimeService;

  beforeEach(() => {
    service = new RealtimeService();
  });

  afterEach(() => {
    service.ngOnDestroy();
  });

  it('initializes with reconnecting status and empty online participants', () => {
    expect(service.connectionStatus()).toBe('reconnecting');
    expect(service.onlineParticipants()).toEqual([]);
  });

  it('resets state on leaveRoom', () => {
    service.leaveRoom();
    expect(service.connectionStatus()).toBe('reconnecting');
    expect(service.onlineParticipants()).toEqual([]);
  });
});
