import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { App } from './app';
import { KeycloakAuth } from './services/auth/keycloak-auth';
import { MindsparkService } from './services/mindspark.service';
import { UserService } from './services/user-service';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        { provide: KeycloakAuth, useValue: { isLoggedIn: false } },
        {
          provide: MindsparkService,
          useValue: {
            health: () => of({ status: 'ok' }),
            listRooms: () => of([]),
          },
        },
        {
          provide: UserService,
          useValue: {
            currentUser: signal(null),
            syncUser: () => of({ id: 1, username: 'test', email: 'test@example.com' }),
          },
        },
      ],
    })
      .compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render the MindSpark workspace', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('Turn bright sparks');
    expect(compiled.querySelector('.brand')?.textContent).toContain('mindspark');
  });
});
