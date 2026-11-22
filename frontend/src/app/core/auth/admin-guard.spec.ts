import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { adminGuard } from './admin.guard';
import { superAdminGuard } from './super-admin.guard';
import { KeycloakAuth } from './keycloak-auth';

describe('Admin Guards', () => {
  let mockAuth: {
    isLoggedIn: boolean;
    isAdmin: () => boolean;
    isSuperAdmin: () => boolean;
  };
  let mockRouter: {
    createUrlTree: (commands: any[]) => UrlTree;
  };

  beforeEach(() => {
    mockAuth = {
      isLoggedIn: false,
      isAdmin: () => false,
      isSuperAdmin: () => false,
    };
    mockRouter = {
      createUrlTree: (commands: any[]) => ({ path: commands.join('/') } as unknown as UrlTree),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: KeycloakAuth, useValue: mockAuth },
        { provide: Router, useValue: mockRouter },
      ],
    });
  });

  describe('adminGuard', () => {
    it('redirects to /admin/login when user is not logged in', () => {
      mockAuth.isLoggedIn = false;
      const result = TestBed.runInInjectionContext(() => adminGuard({} as any, {} as any));
      expect(result).toEqual(expect.objectContaining({ path: '/admin/login' }));
    });

    it('redirects to /admin/forbidden when user is logged in without admin role', () => {
      mockAuth.isLoggedIn = true;
      mockAuth.isAdmin = () => false;
      const result = TestBed.runInInjectionContext(() => adminGuard({} as any, {} as any));
      expect(result).toEqual(expect.objectContaining({ path: '/admin/forbidden' }));
    });

    it('allows access (returns true) when user has admin role', () => {
      mockAuth.isLoggedIn = true;
      mockAuth.isAdmin = () => true;
      const result = TestBed.runInInjectionContext(() => adminGuard({} as any, {} as any));
      expect(result).toBe(true);
    });
  });

  describe('superAdminGuard', () => {
    it('redirects to /admin/login when user is not logged in', () => {
      mockAuth.isLoggedIn = false;
      const result = TestBed.runInInjectionContext(() => superAdminGuard({} as any, {} as any));
      expect(result).toEqual(expect.objectContaining({ path: '/admin/login' }));
    });

    it('redirects to /admin/forbidden when user is regular admin but not super-admin', () => {
      mockAuth.isLoggedIn = true;
      mockAuth.isAdmin = () => true;
      mockAuth.isSuperAdmin = () => false;
      const result = TestBed.runInInjectionContext(() => superAdminGuard({} as any, {} as any));
      expect(result).toEqual(expect.objectContaining({ path: '/admin/forbidden' }));
    });

    it('allows access (returns true) when user has super-admin role', () => {
      mockAuth.isLoggedIn = true;
      mockAuth.isSuperAdmin = () => true;
      const result = TestBed.runInInjectionContext(() => superAdminGuard({} as any, {} as any));
      expect(result).toBe(true);
    });
  });
});
