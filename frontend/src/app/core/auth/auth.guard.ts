import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { KeycloakAuth } from './keycloak-auth';

export const authGuard: CanActivateFn = () => {
  const auth = inject(KeycloakAuth);
  const router = inject(Router);

  if (!auth.isLoggedIn) {
    auth.login();
    return false;
  }
  return true;
};
