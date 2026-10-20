import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, switchMap } from 'rxjs';
import { KeycloakAuth } from './keycloak-auth';

export const Interceptor: HttpInterceptorFn = (request, next) => {
  const keycloakAuth = inject(KeycloakAuth);

  return from(keycloakAuth.getValidToken()).pipe(
    switchMap(token => {
      if (!token) {
        return next(request);
      }

      return next(request.clone({
        setHeaders: { Authorization: `Bearer ${token}` }
      }));
    })
  );
};
