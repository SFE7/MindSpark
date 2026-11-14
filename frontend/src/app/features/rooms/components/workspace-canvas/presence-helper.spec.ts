import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { of, throwError } from 'rxjs';
import { PresenceHelper } from './presence-helper';
import { RoomsService } from '../../data-access/rooms.service';
import { KeycloakAuth } from '../../../../core/auth/keycloak-auth';

describe('PresenceHelper', () => {
  let mockRoomsService: Partial<RoomsService>;
  let mockAuth: Partial<KeycloakAuth>;
  let removedCallback: () => void;
  let helper: PresenceHelper | undefined;
  let originalFetch: typeof fetch;

  beforeEach(() => {
    vi.useFakeTimers();
    sessionStorage.clear();

    mockRoomsService = {
      sendHeartbeat: vi.fn().mockReturnValue(of(void 0)),
      sendLeave: vi.fn().mockReturnValue(of(void 0)),
    };

    mockAuth = {
      currentToken: 'test-token-xyz',
    };

    removedCallback = vi.fn();
    originalFetch = globalThis.fetch;
    globalThis.fetch = vi.fn().mockResolvedValue(new Response());
  });

  afterEach(() => {
    helper?.destroy();
    helper = undefined;
    vi.restoreAllMocks();
    vi.useRealTimers();
    globalThis.fetch = originalFetch;
    sessionStorage.clear();
  });

  it('sends immediate heartbeat on initialization with resolved clientId', () => {
    sessionStorage.setItem('mindspark_presence_client_id', 'existing-client-123');

    helper = new PresenceHelper({
      roomId: 'room-1',
      roomsService: mockRoomsService as RoomsService,
      auth: mockAuth as KeycloakAuth,
      onUserRemoved: removedCallback,
    });

    expect(helper.clientId).toBe('existing-client-123');
    expect(mockRoomsService.sendHeartbeat).toHaveBeenCalledWith('room-1', 'existing-client-123');
  });

  it('generates and persists clientId in sessionStorage if none exists', () => {
    helper = new PresenceHelper({
      roomId: 'room-1',
      roomsService: mockRoomsService as RoomsService,
      auth: mockAuth as KeycloakAuth,
      onUserRemoved: removedCallback,
    });

    expect(helper.clientId).toBeTruthy();
    expect(helper.clientId.length).toBeLessThanOrEqual(64);
    expect(sessionStorage.getItem('mindspark_presence_client_id')).toBe(helper.clientId);
  });

  it('sends periodic heartbeat every 10s', () => {
    helper = new PresenceHelper({
      roomId: 'room-1',
      roomsService: mockRoomsService as RoomsService,
      auth: mockAuth as KeycloakAuth,
      onUserRemoved: removedCallback,
    });

    expect(mockRoomsService.sendHeartbeat).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(10000);
    expect(mockRoomsService.sendHeartbeat).toHaveBeenCalledTimes(2);

    vi.advanceTimersByTime(10000);
    expect(mockRoomsService.sendHeartbeat).toHaveBeenCalledTimes(3);
  });

  it('sends heartbeat on visibilitychange to visible, but does NOT send leave on hidden', () => {
    helper = new PresenceHelper({
      roomId: 'room-1',
      roomsService: mockRoomsService as RoomsService,
      auth: mockAuth as KeycloakAuth,
      onUserRemoved: removedCallback,
    });

    expect(mockRoomsService.sendHeartbeat).toHaveBeenCalledTimes(1);

    // Mock visibilityState to hidden
    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true });
    document.dispatchEvent(new Event('visibilitychange'));

    // Heartbeat count shouldn't change, and fetch (leave) shouldn't be called
    expect(mockRoomsService.sendHeartbeat).toHaveBeenCalledTimes(1);
    expect(globalThis.fetch).not.toHaveBeenCalled();

    // Mock visibilityState back to visible
    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true });
    document.dispatchEvent(new Event('visibilitychange'));

    expect(mockRoomsService.sendHeartbeat).toHaveBeenCalledTimes(2);
  });

  it('calls onUserRemoved when heartbeat returns 403', () => {
    (mockRoomsService.sendHeartbeat as ReturnType<typeof vi.fn>).mockReturnValue(
      throwError(() => ({ status: 403, error: 'Forbidden' }))
    );

    helper = new PresenceHelper({
      roomId: 'room-1',
      roomsService: mockRoomsService as RoomsService,
      auth: mockAuth as KeycloakAuth,
      onUserRemoved: removedCallback,
    });

    expect(removedCallback).toHaveBeenCalled();
  });

  it('sends keepalive leave fetch on destroy and on pagehide', () => {
    helper = new PresenceHelper({
      roomId: 'room-abc',
      roomsService: mockRoomsService as RoomsService,
      auth: mockAuth as KeycloakAuth,
      onUserRemoved: removedCallback,
      clientId: 'c-test-client',
    });

    expect(globalThis.fetch).not.toHaveBeenCalled();

    // Simulate pagehide
    window.dispatchEvent(new Event('pagehide'));

    expect(globalThis.fetch).toHaveBeenCalledWith('/api/rooms/room-abc/presence/leave', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer test-token-xyz',
      },
      body: JSON.stringify({ clientId: 'c-test-client' }),
      keepalive: true,
    });

    helper.destroy();
    expect(globalThis.fetch).toHaveBeenCalledTimes(2);
  });
});
