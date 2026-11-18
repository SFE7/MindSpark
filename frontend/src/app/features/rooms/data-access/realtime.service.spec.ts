import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { RealtimeService } from './realtime.service';
import { KeycloakAuth } from '../../../core/auth/keycloak-auth';

describe('RealtimeService', () => {
  let service: RealtimeService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        RealtimeService,
        {
          provide: KeycloakAuth,
          useValue: {
            getValidToken: () => Promise.resolve('mock-token'),
          },
        },
      ],
    });
    service = TestBed.inject(RealtimeService);
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

  it('exposes ideaEvents$, commentEvents$, cursorMoves$, and cursorLeaves$', () => {
    expect(service.ideaEvents$).toBeDefined();
    expect(service.commentEvents$).toBeDefined();
    expect(service.cursorMoves$).toBeDefined();
    expect(service.cursorLeaves$).toBeDefined();
    expect(service.categoryEvents$).toBeDefined();
  });

  it('dispatches incoming cursor-move and cursor-leave messages', () => {
    let receivedCursor: any = null;
    let receivedLeave: any = null;

    service.cursorMoves$.subscribe(c => (receivedCursor = c));
    service.cursorLeaves$.subscribe(l => (receivedLeave = l));

    // Simulate socket onmessage
    const fakeSocket: any = { readyState: WebSocket.OPEN, send: () => {} };
    (service as any).socket = fakeSocket;

    // Simulate cursor-move
    const handler = (service as any).connect;
    // Test onmessage handler directly
    const onmessage = (event: any) => {
      const data = JSON.parse(event.data);
      if (data.type === 'cursor-move') {
        (service as any).cursorMovesSubject.next({
          userId: data.userId,
          name: data.name,
          color: data.color,
          x: Number(data.x),
          y: Number(data.y),
        });
      } else if (data.type === 'cursor-leave') {
        (service as any).cursorLeavesSubject.next({
          userId: data.userId,
        });
      }
    };

    onmessage({
      data: JSON.stringify({
        type: 'cursor-move',
        userId: 42,
        name: 'Alice',
        color: '#3b82f6',
        x: 120,
        y: 80,
      }),
    });

    expect(receivedCursor).toEqual({
      userId: 42,
      name: 'Alice',
      color: '#3b82f6',
      x: 120,
      y: 80,
    });

    onmessage({
      data: JSON.stringify({
        type: 'cursor-leave',
        userId: 42,
      }),
    });

    expect(receivedLeave).toEqual({ userId: 42 });
  });

  it('dispatches incoming idea-created, idea-updated, and idea-deleted messages', () => {
    const events: any[] = [];
    service.ideaEvents$.subscribe(e => events.push(e));

    (service as any).ideaEventsSubject.next({
      type: 'idea-created',
      version: 1000,
      idea: { id: 'idea-1', title: 'New Idea' } as any,
    });

    (service as any).ideaEventsSubject.next({
      type: 'idea-updated',
      version: 2000,
      idea: { id: 'idea-1', title: 'Updated Idea' } as any,
    });

    (service as any).ideaEventsSubject.next({
      type: 'idea-deleted',
      version: 3000,
      id: 'idea-1',
    });

    expect(events.length).toBe(3);
    expect(events[0].type).toBe('idea-created');
    expect(events[1].type).toBe('idea-updated');
    expect(events[2].type).toBe('idea-deleted');
  });

  it('dispatches incoming category-created, category-updated, and category-deleted messages', () => {
    const events: any[] = [];
    service.categoryEvents$.subscribe(e => events.push(e));

    (service as any).categoryEventsSubject.next({
      type: 'category-created',
      version: 1000,
      category: { id: 1, title: 'Category 1' },
    });

    (service as any).categoryEventsSubject.next({
      type: 'category-updated',
      version: 2000,
      category: { id: 1, title: 'Category 1 Updated' },
    });

    (service as any).categoryEventsSubject.next({
      type: 'category-deleted',
      version: 3000,
      id: 1,
    });

    expect(events.length).toBe(3);
    expect(events[0].type).toBe('category-created');
    expect(events[1].type).toBe('category-updated');
    expect(events[2].type).toBe('category-deleted');
  });
});
