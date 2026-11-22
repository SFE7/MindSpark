import { Component, inject, signal, OnInit, OnDestroy, HostListener, ElementRef } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { KeycloakAuth } from '../../core/auth/keycloak-auth';
import { UserService } from '../../core/user-service';
import { NotificationService } from '../../core/notifications/notification.service';
import { NotificationItem } from '../../core/notifications/notification.models';

@Component({
  imports: [RouterLink, RouterLinkActive],
  selector: 'app-header',
  styleUrl: './header.scss',
  templateUrl: './header.html',
})
export class Header implements OnInit, OnDestroy {
  private readonly auth = inject(KeycloakAuth);
  private readonly userService = inject(UserService);
  private readonly router = inject(Router);
  protected readonly notificationService = inject(NotificationService);
  private readonly elementRef = inject(ElementRef);

  protected readonly currentUser = this.userService.currentUser;
  protected readonly isLoggedIn = this.auth.isLoggedIn;
  protected readonly notifications = this.notificationService.notifications;
  protected readonly unreadCount = this.notificationService.unreadCount;
  protected readonly isPanelOpen = signal<boolean>(false);
  protected readonly isMobileMenuOpen = signal<boolean>(false);

  ngOnInit(): void {
    if (this.isLoggedIn) {
      this.notificationService.loadNotifications();
      this.notificationService.startPolling();
    }
  }

  ngOnDestroy(): void {
    this.notificationService.stopPolling();
  }

  get isAdmin(): boolean {
    return this.auth.isAdmin ? this.auth.isAdmin() : false;
  }

  get isHomePage(): boolean {
    const url = this.router.url ? this.router.url.split('?')[0] : '/';
    return url === '/' || url === '';
  }

  togglePanel(event?: Event): void {
    event?.stopPropagation();
    const nextState = !this.isPanelOpen();
    this.isPanelOpen.set(nextState);
    if (nextState) {
      this.notificationService.loadNotifications();
    }
  }

  closePanel(): void {
    this.isPanelOpen.set(false);
  }

  toggleMobileMenu(event?: Event): void {
    event?.stopPropagation();
    this.isMobileMenuOpen.update(open => !open);
    if (this.isMobileMenuOpen()) {
      this.closePanel();
    }
  }

  closeMobileMenu(): void {
    this.isMobileMenuOpen.set(false);
  }

  markAllAsRead(event?: Event): void {
    event?.stopPropagation();
    this.notificationService.markAllRead();
  }

  onItemClick(item: NotificationItem): void {
    if (!item.isRead) {
      this.notificationService.markAsRead(item.id);
    }
    this.closePanel();
    if (item.actionUrl) {
      void this.router.navigateByUrl(item.actionUrl);
    }
  }

  onDelete(id: string, event: Event): void {
    event.stopPropagation();
    this.notificationService.deleteNotification(id);
  }

  formatTime(isoString: string): string {
    if (!isoString) return '';
    try {
      const date = new Date(isoString);
      const now = new Date();
      const diffMs = now.getTime() - date.getTime();
      const diffSec = Math.floor(diffMs / 1000);
      if (diffSec < 60) return 'just now';
      const diffMin = Math.floor(diffSec / 60);
      if (diffMin < 60) return `${diffMin}m ago`;
      const diffHrs = Math.floor(diffMin / 60);
      if (diffHrs < 24) return `${diffHrs}h ago`;
      const diffDays = Math.floor(diffHrs / 24);
      return `${diffDays}d ago`;
    } catch {
      return '';
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      if (this.isPanelOpen()) {
        this.closePanel();
      }
      if (this.isMobileMenuOpen()) {
        this.closeMobileMenu();
      }
    }
  }

  login(): void {
    void this.auth.login();
  }

  register(): void {
    void this.auth.register();
  }

  logout(): void {
    this.notificationService.stopPolling();
    this.userService.clearCurrentUser();
    void this.auth.logout();
  }
}
