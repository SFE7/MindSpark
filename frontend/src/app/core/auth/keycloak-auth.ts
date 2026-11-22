import { Injectable } from '@angular/core';
import Keycloak from 'keycloak-js';

@Injectable({
  providedIn: 'root'
})
export class KeycloakAuth {
  private readonly keycloak = new Keycloak({
    url: `${window.location.protocol}//${window.location.hostname}:8180`,
    realm: 'mindspark',
    clientId: 'frontend'
  });

  init(): Promise<boolean> {
    // DEV ONLY, remove when the app is served over HTTPS and re-enable PKCE S256 everywhere.
    const isSecure = typeof window !== 'undefined' && window.isSecureContext;
    return this.keycloak.init({
      onLoad: 'check-sso',
      pkceMethod: isSecure ? 'S256' : false,
      checkLoginIframe: false
    });
  }

  async getValidToken(): Promise<string | undefined> {
    if (!this.keycloak.authenticated) {
      return undefined;
    }

    await this.keycloak.updateToken(30);
    return this.keycloak.token;
  }

  async refreshToken(): Promise<string | undefined> {
    if (!this.keycloak.authenticated) {
      return undefined;
    }
    try {
      await this.keycloak.updateToken(-1);
    } catch {
      // Gracefully handle if refresh token exchange fails
    }
    return this.keycloak.token;
  }

  get currentToken(): string | undefined {
    return this.keycloak.token;
  }

  get isLoggedIn(): boolean {
    return this.keycloak.authenticated;
  }

  get username(): string | undefined {
    return this.keycloak.tokenParsed?.['preferred_username'] as
      string | undefined;
  }

  getRealmRoles(): string[] {
    const realmAccess = this.keycloak.tokenParsed?.['realm_access'] as { roles?: string[] } | undefined;
    const roles = realmAccess?.roles;
    return Array.isArray(roles) ? roles : [];
  }

  hasRole(role: string): boolean {
    if (!role) return false;
    const target = role.toLowerCase().replace(/[-_]/g, '');
    return this.getRealmRoles().some(r => r.toLowerCase().replace(/[-_]/g, '') === target);
  }

  isAdmin(): boolean {
    return this.hasRole('admin') || this.hasRole('super-admin');
  }

  isSuperAdmin(): boolean {
    return this.hasRole('super-admin');
  }

  login(): Promise<void> {
    return this.keycloak.login({ prompt: 'login' });
  }

  register(): Promise<void> {
    if (typeof this.keycloak.register === 'function') {
      return this.keycloak.register({ redirectUri: window.location.origin });
    }
    return this.login();
  }

  logout(): Promise<void> {
    return this.keycloak.logout({
      redirectUri: window.location.origin
    });
  }
}
