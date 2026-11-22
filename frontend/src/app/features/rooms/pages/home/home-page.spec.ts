import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { HomePageComponent } from './home-page';
import { RoomsService } from '../../data-access/rooms.service';
import { KeycloakAuth } from '../../../../core/auth/keycloak-auth';
import { UserService } from '../../../../core/user-service';

describe('HomePageComponent - Limit Enforcements', () => {
  let component: HomePageComponent;
  let fixture: ComponentFixture<HomePageComponent>;
  let mockRoomsService: any;
  let mockAuth: { isLoggedIn: boolean };
  let mockUserService: any;

  beforeEach(async () => {
    mockRoomsService = {
      health: () => of({ status: 'UP' }),
      listRooms: () => of([]),
      createRoom: () => of({ id: 'r1', name: 'Room 1', invitationCode: 'ABCDEF', closed: false, memberCount: 1 }),
      joinRoom: (code: string) => of({ id: 'r1', name: 'Room 1', invitationCode: code, closed: false, memberCount: 2 }),
      reopenRoom: () => of({ id: 'r1', name: 'Room 1', invitationCode: 'ABCDEF', closed: false, memberCount: 1 }),
      deleteRoom: () => of(void 0),
    };

    mockAuth = { isLoggedIn: true };
    mockUserService = {
      syncUser: () => of({ id: 1, keycloakId: 'user-sub', username: 'testuser', email: 'test@example.com' }),
      clearCurrentUser: () => undefined,
    };

    await TestBed.configureTestingModule({
      imports: [HomePageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'room/:id', children: [] }, { path: 'billing', children: [] }]),
        { provide: RoomsService, useValue: mockRoomsService },
        { provide: KeycloakAuth, useValue: mockAuth },
        { provide: UserService, useValue: mockUserService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(HomePageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('displays inline limit-alert with Upgrade Plan button on ROOM_LIMIT_REACHED (403)', () => {
    const error403 = new HttpErrorResponse({
      status: 403,
      error: {
        error: 'ROOM_LIMIT_REACHED',
        message: 'You have reached your active room limit for plan FREE (3/3).',
        limit: 3,
        current: 3,
        upgradeRequired: true,
      },
    });

    mockRoomsService.createRoom = () => throwError(() => error403);

    component['roomName'].set('New Room');
    component['createRoom']();
    fixture.detectChanges();

    expect(component['roomLimitHit']()).toEqual({
      current: 3,
      limit: 3,
      message: 'You have reached your active room limit for plan FREE (3/3).',
    });

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Room limit reached (3/3 active rooms)');
    expect(element.textContent).toContain('Upgrade Plan');
  });

  it('displays clear error on PARTICIPANT_LIMIT_REACHED (403) on room join', () => {
    const error403 = new HttpErrorResponse({
      status: 403,
      error: {
        error: 'PARTICIPANT_LIMIT_REACHED',
        message: "This room has reached its host's participant limit (5/5).",
        limit: 5,
        current: 5,
      },
    });

    mockRoomsService.joinRoom = () => throwError(() => error403);

    component['invitationCode'].set('ABCDEF');
    component['joinRoom']();
    fixture.detectChanges();

    expect(component['error']()).toContain("This room has reached its host's participant limit (5/5).");
  });

  it('handles 6-slot join code input typing, paste (B7K9Q2), and submits joined code cleanly', () => {
    const joinSpy = vi.spyOn(mockRoomsService, 'joinRoom');

    // Simulate typing single characters
    component['onOtpInput'](0, { target: { value: 'b' } } as unknown as Event);
    expect(component['otpSlots']()[0]).toBe('B');
    expect(component['invitationCode']()).toBe('B');

    // Simulate paste of full 6-character code
    const fakeClipboard = {
      preventDefault: () => undefined,
      clipboardData: {
        getData: () => 'B7K9Q2',
      },
    } as unknown as ClipboardEvent;

    component['onOtpPaste'](fakeClipboard, 0);
    expect(component['otpSlots']()).toEqual(['B', '7', 'K', '9', 'Q', '2']);
    expect(component['invitationCode']()).toBe('B7K9Q2');

    // Trigger join
    component['joinRoom']();
    expect(joinSpy).toHaveBeenCalledWith('B7K9Q2');
  });

  it('handles backspace and navigation in OTP slots', () => {
    component['otpSlots'].set(['B', '7', '', '', '', '']);

    // Backspace on non-empty slot clears it
    component['onOtpKeyDown'](1, { key: 'Backspace', preventDefault: () => undefined } as KeyboardEvent);
    expect(component['otpSlots']()[1]).toBe('');

    // Arrow keys do not error
    component['onOtpKeyDown'](1, { key: 'ArrowLeft', preventDefault: () => undefined } as KeyboardEvent);
    component['onOtpKeyDown'](1, { key: 'ArrowRight', preventDefault: () => undefined } as KeyboardEvent);
  });
});
