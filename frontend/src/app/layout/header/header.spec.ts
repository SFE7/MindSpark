import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { KeycloakAuth } from '../../core/auth/keycloak-auth';
import { UserService } from '../../core/user-service';
import { NotificationService } from '../../core/notifications/notification.service';
import { Header } from './header';

describe('Header', () => {
  let component: Header;
  let fixture: ComponentFixture<Header>;
  let mockAuth: any;
  let mockUserService: any;
  let mockNotificationService: any;

  beforeEach(async () => {
    mockAuth = {
      isLoggedIn: false,
      login: () => Promise.resolve(),
      register: () => Promise.resolve(),
      logout: () => Promise.resolve(),
      isAdmin: () => false,
    };

    mockUserService = {
      currentUser: signal(null),
      clearCurrentUser: () => undefined,
    };

    mockNotificationService = {
      notifications: signal([]),
      unreadCount: signal(0),
      loading: signal(false),
      loadNotifications: () => undefined,
      refreshUnreadCount: () => undefined,
      startPolling: () => undefined,
      stopPolling: () => undefined,
      markAsRead: () => undefined,
      markAllRead: () => undefined,
      deleteNotification: () => undefined,
    };

    await TestBed.configureTestingModule({
      imports: [Header],
      providers: [
        provideRouter([]),
        { provide: KeycloakAuth, useValue: mockAuth },
        { provide: UserService, useValue: mockUserService },
        { provide: NotificationService, useValue: mockNotificationService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Header);
    component = fixture.componentInstance;
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('displays Log in and Create account when logged out', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Log in');
    expect(el.textContent).toContain('Create account');
    expect(el.querySelector('.login-button')).toBeTruthy();
    expect(el.querySelector('.register-button')).toBeTruthy();
  });

  it('triggers login and register methods', () => {
    const loginSpy = vi.spyOn(mockAuth, 'login');
    const registerSpy = vi.spyOn(mockAuth, 'register');

    component.login();
    expect(loginSpy).toHaveBeenCalled();

    component.register();
    expect(registerSpy).toHaveBeenCalled();
  });

  it('displays nav links, notification bell, username, and logout button when logged in', async () => {
    mockAuth.isLoggedIn = true;
    mockUserService.currentUser.set({ username: 'stargazer' });
    mockNotificationService.unreadCount.set(2);

    const loggedInFixture = TestBed.createComponent(Header);
    loggedInFixture.detectChanges();
    await loggedInFixture.whenStable();

    const el = loggedInFixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Rooms');
    expect(el.textContent).toContain('Sessions');
    expect(el.textContent).toContain('Plans & Billing');
    expect(el.textContent).toContain('stargazer');
    expect(el.textContent).toContain('Log out');
    expect(el.querySelector('.bell-btn')).toBeTruthy();
    expect(el.querySelector('.bell-badge')?.textContent).toContain('2');
  });

  it('toggles mobile menu panel when hamburger button is clicked', () => {
    expect(component['isMobileMenuOpen']()).toBe(false);
    component.toggleMobileMenu();
    expect(component['isMobileMenuOpen']()).toBe(true);
    component.closeMobileMenu();
    expect(component['isMobileMenuOpen']()).toBe(false);
  });
});
