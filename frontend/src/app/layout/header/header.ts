import { Component, inject } from '@angular/core';
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

  protected readonly currentUser = this.userService.currentUser;
  protected readonly isLoggedIn = this.auth.isLoggedIn;

  login(): void {
    void this.auth.login();
  }

  register(): void {
    void this.auth.register();
  }

  logout(): void {
    this.userService.clearCurrentUser();
    void this.auth.logout();
  }
}
