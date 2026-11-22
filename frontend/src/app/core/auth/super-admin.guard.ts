import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { KeycloakAuth } from './keycloak-auth';

export const superAdminGuard: CanActivateFn = () => {
  const auth = inject(KeycloakAuth);
  const router = inject(Router);

  if (!auth.isLoggedIn) {
    return router.createUrlTree(['/admin/login']);
  }

  if (!auth.isSuperAdmin()) {
    return router.createUrlTree(['/admin/forbidden']);
  }

  return true;
};
