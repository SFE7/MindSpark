import { Component, ElementRef, HostListener, inject, signal } from '@angular/core';
import { KeycloakAuth } from '../../core/auth/keycloak-auth';
import { UserService } from '../../core/user-service';

@Component({
  imports: [],
  selector: 'app-header',
  styleUrl: './header.scss',
  templateUrl: './header.html',
})
export class Header {
  private readonly auth = inject(KeycloakAuth);
  private readonly userService = inject(UserService);
  private readonly hostRef = inject(ElementRef<HTMLElement>);

  protected readonly currentUser = this.userService.currentUser;
  protected readonly isLoggedIn = this.auth.isLoggedIn;
  protected readonly accountMenuOpen = signal(false);

  constructor() {
    if (typeof window !== 'undefined' && sessionStorage.getItem('pending_account_deletion') === 'true') {
      sessionStorage.removeItem('pending_account_deletion');
      if (!this.auth.isLoggedIn) {
        this.userService.clearCurrentUser();
      }
    }
  }

  protected toggleAccountMenu(event: MouseEvent): void {
    event.stopPropagation();
    this.accountMenuOpen.update((open) => !open);
  }

  protected closeAccountMenu(): void {
    this.accountMenuOpen.set(false);
  }

  @HostListener('document:click', ['$event'])
  protected onDocumentClick(event: MouseEvent): void {
    if (!this.hostRef.nativeElement.contains(event.target as Node)) {
      this.closeAccountMenu();
    }
  }

  @HostListener('document:keydown.escape')
  protected onEscapeKey(): void {
    this.closeAccountMenu();
  }

  login(): void {
    void this.auth.login();
  }

  register(): void {
    void this.auth.register();
  }

  changePassword(): void {
    this.closeAccountMenu();
    void this.auth.updatePassword();
  }

  deleteAccount(): void {
    this.closeAccountMenu();
    const confirmed = window.confirm(
      'Are you sure you want to delete your account? This action cannot be undone.'
    );
    if (!confirmed) {
      return;
    }
    this.userService.clearCurrentUser();
    void this.auth.deleteAccount();
  }

  logout(): void {
    this.closeAccountMenu();
    this.userService.clearCurrentUser();
    void this.auth.logout();
  }
}
