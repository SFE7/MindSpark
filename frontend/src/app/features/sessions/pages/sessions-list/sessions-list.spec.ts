import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SessionsListComponent } from './sessions-list';
import { SessionHistoryService } from '../../../../core/sessions/session-history.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

describe('SessionsListComponent', () => {
  let component: SessionsListComponent;
  let fixture: ComponentFixture<SessionsListComponent>;
  let sessionService: SessionHistoryService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SessionsListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        SessionHistoryService,
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SessionsListComponent);
    component = fixture.componentInstance;
    sessionService = TestBed.inject(SessionHistoryService);
  });

  it('creates component and loads initial snapshots', () => {
    const spy = vi.spyOn(sessionService, 'listSnapshots').mockReturnValue(of([]));
    fixture.detectChanges();
    expect(component).toBeTruthy();
    expect(spy).toHaveBeenCalled();
  });

  it('triggers delete confirmation dialog and executes delete', () => {
    const mockSnap = {
      id: 'snap-1',
      roomId: 'r1',
      roomName: 'Room 1',
      invitationCode: 'CODE1',
      title: 'To Delete',
      savedByUserId: 'u1',
      ownerUserId: 'u1',
      ideaCount: 1,
      categoryCount: 1,
      totalVotes: 1,
      participantCount: 1,
      createdAt: '2026-11-21T00:00:00Z',
      hasAiSummary: false,
    };

    component.confirmDelete(mockSnap);
    expect(component.deleteTarget()).toBe(mockSnap);

    const deleteSpy = vi.spyOn(sessionService, 'deleteSnapshot').mockReturnValue(of(void 0));
    component.executeDelete();
    expect(deleteSpy).toHaveBeenCalledWith('snap-1');
    expect(component.deleteTarget()).toBeNull();
  });

  it('toggles export dropdown menu', () => {
    const event = new MouseEvent('click');
    component.toggleExportMenu('snap-1', event);
    expect(component.activeExportId()).toBe('snap-1');

    component.toggleExportMenu('snap-1', event);
    expect(component.activeExportId()).toBeNull();
  });
});
