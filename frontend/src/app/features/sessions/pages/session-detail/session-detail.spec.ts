import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SessionDetailComponent } from './session-detail';
import { SessionHistoryService } from '../../../../core/sessions/session-history.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

describe('SessionDetailComponent', () => {
  let component: SessionDetailComponent;
  let fixture: ComponentFixture<SessionDetailComponent>;
  let sessionService: SessionHistoryService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SessionDetailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        SessionHistoryService,
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SessionDetailComponent);
    component = fixture.componentInstance;
    sessionService = TestBed.inject(SessionHistoryService);
  });

  it('creates component and groups categories and computes top ideas', () => {
    const mockDetail = {
      id: 'snap-1',
      roomId: 'room-1',
      roomName: 'Sprint 3 Room',
      invitationCode: 'CODE1',
      title: 'Sprint 3 Snapshot',
      savedByUserId: 'u1',
      ownerUserId: 'u1',
      ideaCount: 2,
      categoryCount: 1,
      totalVotes: 8,
      participantCount: 2,
      createdAt: '2026-11-21T00:00:00Z',
      hasAiSummary: true,
      aiSummary: 'Summary text',
      snapshotData: {
        roomId: 'room-1',
        roomName: 'Sprint 3 Room',
        invitationCode: 'CODE1',
        closed: false,
        createdAt: '2026-11-21T00:00:00Z',
        ownerKeycloakId: 'u1',
        ownerName: 'Ahmed',
        participants: [],
        categories: [{ id: 1, name: 'Frontend' }],
        ideas: [
          {
            id: 'i1',
            title: 'Idea 1',
            text: 'Text 1',
            categoryId: 1,
            categoryName: 'Frontend',
            authorName: 'Ahmed',
            voteCount: 5,
            createdAt: '2026-11-21T00:00:00Z',
            comments: [],
          },
          {
            id: 'i2',
            title: 'Idea 2',
            text: 'Text 2',
            categoryId: null,
            categoryName: null,
            authorName: 'Sarah',
            voteCount: 3,
            createdAt: '2026-11-21T00:00:00Z',
            comments: [],
          },
        ],
      },
    };

    sessionService.selectedSnapshot.set(mockDetail);
    fixture.detectChanges();

    expect(component.topVotedIdeas().length).toBe(2);
    expect(component.topVotedIdeas()[0].title).toBe('Idea 1');
    expect(component.groupedCategories().length).toBe(1);
    expect(component.groupedCategories()[0].ideas.length).toBe(1);
    expect(component.uncategorizedIdeas().length).toBe(1);
  });

  it('exportSession delegates to service', () => {
    const exportSpy = vi.spyOn(sessionService, 'exportSnapshot').mockReturnValue(of(new Blob()));
    // Set private sessionId or call method
    (component as any).sessionId = 'snap-1';
    component.exportSession('md');
    expect(exportSpy).toHaveBeenCalledWith('snap-1', 'md');
  });
});
