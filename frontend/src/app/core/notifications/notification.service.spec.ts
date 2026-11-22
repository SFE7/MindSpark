import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { NotificationService } from './notification.service';
import { KeycloakAuth } from '../auth/keycloak-auth';
import { NotificationItem, NotificationListResponse } from './notification.models';

describe('NotificationService', () => {
  let service: NotificationService;
  let httpTesting: HttpTestingController;

  const mockItem: NotificationItem = {
    id: 'n-123',
    eventId: 'e-1',
    userId: 'u-1',
    eventType: 'ROOM_CLOSED',
    title: 'Room Closed',
    message: 'Sprint room is closed',
    actionUrl: '/room/r-1',
    isRead: false,
    createdAt: new Date().toISOString()
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        NotificationService,
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: KeycloakAuth,
          useValue: { isLoggedIn: true }
        }
      ]
    });

    service = TestBed.inject(NotificationService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    service.stopPolling();
    httpTesting.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
    expect(service.notifications().length).toBe(0);
    expect(service.unreadCount()).toBe(0);
  });

  it('loadNotifications should update notifications and unreadCount', () => {
    service.loadNotifications();

    const req = httpTesting.expectOne('/api/notifications?limit=30&unreadOnly=false');
    expect(req.request.method).toBe('GET');

    const mockResponse: NotificationListResponse = {
      items: [mockItem],
      unreadCount: 1
    };
    req.flush(mockResponse);

    expect(service.notifications().length).toBe(1);
    expect(service.notifications()[0].id).toBe('n-123');
    expect(service.unreadCount()).toBe(1);
  });

  it('markAsRead should update item isRead and decrement unreadCount', () => {
    service.notifications.set([{ ...mockItem }]);
    service.unreadCount.set(1);

    service.markAsRead('n-123');

    const req = httpTesting.expectOne('/api/notifications/n-123/read');
    expect(req.request.method).toBe('PATCH');
    req.flush({ ...mockItem, isRead: true });

    expect(service.notifications()[0].isRead).toBe(true);
    expect(service.unreadCount()).toBe(0);
  });

  it('markAllRead should set all items read and zero unreadCount', () => {
    service.notifications.set([
      { ...mockItem, id: 'n-1' },
      { ...mockItem, id: 'n-2' }
    ]);
    service.unreadCount.set(2);

    service.markAllRead();

    const req = httpTesting.expectOne('/api/notifications/read-all');
    expect(req.request.method).toBe('PATCH');
    req.flush({ updatedCount: 2 });

    expect(service.notifications().every(i => i.isRead)).toBe(true);
    expect(service.unreadCount()).toBe(0);
  });

  it('deleteNotification should remove item from notifications signal', () => {
    service.notifications.set([{ ...mockItem }]);
    service.unreadCount.set(1);

    service.deleteNotification('n-123');

    const req = httpTesting.expectOne('/api/notifications/n-123');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);

    expect(service.notifications().length).toBe(0);
    expect(service.unreadCount()).toBe(0);
  });
});
