import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { KeycloakAuth } from '../../services/auth/keycloak-auth';
import { UserService } from '../../services/user-service';
import { Header } from './header';

describe('Header', () => {
  let component: Header;
  let fixture: ComponentFixture<Header>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Header],
      providers: [
        provideRouter([]),
        { provide: KeycloakAuth, useValue: { isLoggedIn: false } },
        {
          provide: UserService,
          useValue: {
            currentUser: signal(null),
            clearCurrentUser: () => undefined,
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Header);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
