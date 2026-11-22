import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  SessionHistoryService,
  extractFilenameFromHeader,
} from './session-history.service';
import { BackendRoomExportDto, CreateSnapshotRequest, SessionSnapshotDetail, SessionSnapshotSummary } from './session.models';

describe('SessionHistoryService', () => {
  let service: SessionHistoryService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        SessionHistoryService,
      ],
    });

    service = TestBed.inject(SessionHistoryService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('extractFilenameFromHeader handles RFC-5987 UTF-8 encoding properly', () => {
    const header = "attachment; filename=\"report.md\"; filename*=UTF-8''%D8%AC%D9%84%D8%B3%D8%A9.md";
    expect(extractFilenameFromHeader(header, 'fallback.md')).toBe('جلسة.md');
  });

  it('extractFilenameFromHeader extracts standard quoted filename', () => {
    const header = 'attachment; filename="sprint-plan.json"';
    expect(extractFilenameFromHeader(header, 'fallback.json')).toBe('sprint-plan.json');
  });

  it('extractFilenameFromHeader falls back when header is null or malformed', () => {
    expect(extractFilenameFromHeader(null, 'fallback.csv')).toBe('fallback.csv');
    expect(extractFilenameFromHeader('', 'fallback.csv')).toBe('fallback.csv');
  });

  it('listSnapshots fetches accessible snapshots and updates signal', () => {
    const mockSnapshots: SessionSnapshotSummary[] = [
      {
        id: '123',
        roomId: 'room-1',
        roomName: 'Sprint 3 Room',
        invitationCode: 'CODE1',
        title: 'Snapshot 1',
        savedByUserId: 'user-1',
        ownerUserId: 'user-1',
        ideaCount: 5,
        categoryCount: 2,
        totalVotes: 10,
        participantCount: 3,
        createdAt: '2026-11-21T00:00:00Z',
        hasAiSummary: true,
      },
    ];

    service.listSnapshots('room-1', 'test').subscribe(res => {
      expect(res).toEqual(mockSnapshots);
      expect(service.snapshots()).toEqual(mockSnapshots);
    });

    const req = httpMock.expectOne('/api/sessions/snapshots?roomId=room-1&q=test');
    expect(req.request.method).toBe('GET');
    req.flush(mockSnapshots);
  });

  it('getSnapshot fetches single snapshot detail and sets selectedSnapshot', () => {
    const mockDetail: SessionSnapshotDetail = {
      id: 'snap-1',
      roomId: 'room-1',
      roomName: 'Architecture',
      invitationCode: 'ARCH1',
      title: 'Full Architecture',
      savedByUserId: 'user-1',
      ownerUserId: 'user-1',
      ideaCount: 1,
      categoryCount: 1,
      totalVotes: 3,
      participantCount: 2,
      createdAt: '2026-11-21T00:00:00Z',
      hasAiSummary: false,
      snapshotData: {
        roomId: 'room-1',
        roomName: 'Architecture',
        invitationCode: 'ARCH1',
        closed: false,
        createdAt: '2026-11-21T00:00:00Z',
        ownerKeycloakId: 'user-1',
        ownerName: 'Ahmed',
        participants: [],
        categories: [],
        ideas: [],
      },
    };

    service.getSnapshot('snap-1').subscribe(res => {
      expect(res).toEqual(mockDetail);
      expect(service.selectedSnapshot()).toEqual(mockDetail);
    });

    const req = httpMock.expectOne('/api/sessions/snapshots/snap-1');
    expect(req.request.method).toBe('GET');
    req.flush(mockDetail);
  });

  it('saveSnapshot posts request and updates snapshot list signal', () => {
    const reqPayload: CreateSnapshotRequest = {
      roomId: 'room-1',
      title: 'New Snapshot',
      notes: 'Some notes',
    };

    const mockSaved: SessionSnapshotDetail = {
      id: 'snap-new',
      roomId: 'room-1',
      roomName: 'Architecture',
      invitationCode: 'ARCH1',
      title: 'New Snapshot',
      notes: 'Some notes',
      savedByUserId: 'user-1',
      ownerUserId: 'user-1',
      ideaCount: 0,
      categoryCount: 0,
      totalVotes: 0,
      participantCount: 1,
      createdAt: '2026-11-21T00:00:00Z',
      hasAiSummary: false,
      snapshotData: {
        roomId: 'room-1',
        roomName: 'Architecture',
        invitationCode: 'ARCH1',
        closed: false,
        createdAt: '2026-11-21T00:00:00Z',
        ownerKeycloakId: 'user-1',
        ownerName: 'Ahmed',
        participants: [],
        categories: [],
        ideas: [],
      },
    };

    service.saveSnapshot(reqPayload).subscribe(res => {
      expect(res).toEqual(mockSaved);
      expect(service.snapshots().some(s => s.id === 'snap-new')).toBe(true);
    });

    const req = httpMock.expectOne('/api/sessions/snapshots');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(reqPayload);
    req.flush(mockSaved);
  });

  it('deleteSnapshot sends DELETE and removes snapshot from list signal', () => {
    service.snapshots.set([
      {
        id: 'to-delete',
        roomId: 'r1',
        roomName: 'R1',
        invitationCode: 'C1',
        title: 'Delete Me',
        savedByUserId: 'u1',
        ownerUserId: 'u1',
        ideaCount: 0,
        categoryCount: 0,
        totalVotes: 0,
        participantCount: 1,
        createdAt: '2026-11-21T00:00:00Z',
        hasAiSummary: false,
      },
    ]);

    service.deleteSnapshot('to-delete').subscribe();

    const req = httpMock.expectOne('/api/sessions/snapshots/to-delete');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);

    expect(service.snapshots().length).toBe(0);
  });

  it('exportSnapshot downloads blob and parses Content-Disposition', () => {
    const mockBlob = new Blob(['# Markdown Content'], { type: 'text/markdown' });

    service.exportSnapshot('snap-1', 'md').subscribe(blob => {
      expect(blob).toBeTruthy();
    });

    const req = httpMock.expectOne('/api/sessions/snapshots/snap-1/export?format=md');
    expect(req.request.method).toBe('GET');
    req.flush(mockBlob, {
      headers: {
        'content-disposition': 'attachment; filename="session-report.md"',
      },
    });
  });

  it('exportLiveRoom downloads blob and triggers download', () => {
    const mockBlob = new Blob(['ID,Category\n1,Backend'], { type: 'text/csv' });

    service.exportLiveRoom('room-1', 'csv').subscribe(blob => {
      expect(blob).toBeTruthy();
    });

    const req = httpMock.expectOne('/api/sessions/rooms/room-1/export?format=csv');
    expect(req.request.method).toBe('GET');
    req.flush(mockBlob, {
      headers: {
        'content-disposition': 'attachment; filename="room-live.csv"',
      },
    });
  });
});
