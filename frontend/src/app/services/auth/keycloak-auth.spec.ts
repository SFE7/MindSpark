import { TestBed } from '@angular/core/testing';
import { KeycloakAuth } from './keycloak-auth';

describe('KeycloakAuth', () => {
  let service: KeycloakAuth;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(KeycloakAuth);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
