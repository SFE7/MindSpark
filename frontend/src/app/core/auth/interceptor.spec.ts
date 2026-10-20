import {  TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Interceptor } from './interceptor';
import { KeycloakAuth } from './keycloak-auth';

describe('Interceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([Interceptor])),
        provideHttpClientTesting(),
        { provide: KeycloakAuth, useValue: { getValidToken: () => Promise.resolve('test-token') } },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('attaches the current Keycloak access token', async () => {
    const response = http.get('/api/rooms').subscribe();
    await Promise.resolve();

    const request = httpTesting.expectOne('/api/rooms');
    expect(request.request.headers.get('Authorization')).toBe('Bearer test-token');
    request.flush([]);
    response.unsubscribe();
  });
});
