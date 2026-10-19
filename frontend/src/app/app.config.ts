import { ApplicationConfig, provideAppInitializer, inject, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { KeycloakAuth } from './services/auth/keycloak-auth';
import { Interceptor } from './services/auth/interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideAppInitializer(
  () => inject(KeycloakAuth).init()

),
provideHttpClient(
  withInterceptors([Interceptor])
)
  ]
};
