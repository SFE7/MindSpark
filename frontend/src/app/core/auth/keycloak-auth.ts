
import { Injectable } from '@angular/core';
import Keycloak from 'keycloak-js';

@Injectable({
  providedIn: 'root'
})
export class KeycloakAuth {
  private readonly keycloak = new Keycloak({
    url: 'http://localhost:8180',
    realm: 'mindspark',
    clientId: 'frontend'
  });

  init(): Promise<boolean> {
    return this.keycloak.init({
      onLoad: 'check-sso',
      pkceMethod: 'S256',
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
    return this.keycloak.login({prompt: 'login'});
  }

  register(): Promise<void> {
    return this.keycloak.register({redirectUri: window.location.origin});
  }

  logout(): Promise<void> {
    return this.keycloak.logout({
      redirectUri: window.location.origin
    });
  }
}



