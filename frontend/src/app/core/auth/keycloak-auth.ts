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

  async init(): Promise<boolean> {
    // DEV ONLY, remove when the app is served over HTTPS and re-enable PKCE S256 everywhere.
    const isSecure = typeof window !== 'undefined' && window.isSecureContext;
    const authenticated = await this.keycloak.init({
      onLoad: 'check-sso',
      pkceMethod: isSecure ? 'S256' : false,
      checkLoginIframe: false
    });

    if (typeof window !== 'undefined' && sessionStorage.getItem('pending_account_deletion') === 'true') {
      sessionStorage.removeItem('pending_account_deletion');
      if (!authenticated) {
        sessionStorage.clear();
      }
    }

    return authenticated;
  }

  async getValidToken(): Promise<string | undefined> {
    if (!this.keycloak.authenticated) {
      return undefined;
    }

    await this.keycloak.updateToken(30);
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

  login(): Promise<void> {
    return this.keycloak.login({ prompt: 'login' });
  }

  register(): Promise<void> {
    return this.keycloak.register({ redirectUri: window.location.origin });
  }

  updatePassword(): Promise<void> {
    return this.keycloak.login({
      action: 'UPDATE_PASSWORD',
      redirectUri: window.location.origin
    });
  }

  deleteAccount(): Promise<void> {
    if (typeof window !== 'undefined') {
      sessionStorage.setItem('pending_account_deletion', 'true');
    }
    return this.keycloak.login({
      action: 'delete_account',
      redirectUri: window.location.origin
    });
  }

  logout(): Promise<void> {
    return this.keycloak.logout({
      redirectUri: window.location.origin
    });
  }
}
