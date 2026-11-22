import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { describe, expect, it, afterEach, beforeEach } from 'vitest';
import { VotePoolService } from './vote-pool.service';
import { CreateVotePoolRequest, VotePool } from './vote-pool.model';

describe('VotePoolService', () => {
  let service: VotePoolService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        VotePoolService,
      ],
    });

    service = TestBed.inject(VotePoolService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    service.reset();
    TestBed.resetTestingModule();
  });

  it('loadPool fetches active pool and updates signals', () => {
    const mockPool: VotePool = {
      id: 'pool-1',
      roomId: 'room-1',
      title: 'Sprint 3 Prioritization',
      votesPerParticipant: 3,
      allowMultipleVotesPerIdea: true,
      status: 'OPEN',
      createdById: 1,
      createdAt: '2026-11-21T00:00:00Z',
      myVotesUsed: 1,
      myVotesRemaining: 2,
      myVotesByIdeaId: { 'idea-1': 1 },
      tallies: [{ ideaId: 'idea-1', ideaTitle: 'Auth Flow', poolVotes: 3 }],
    };

    service.loadPool('room-1', true).subscribe((pool) => {
      expect(pool).toEqual(mockPool);
    });

    const req = httpMock.expectOne('/api/rooms/room-1/vote-pools/active?includeLatestClosed=true');
    expect(req.request.method).toBe('GET');
    req.flush(mockPool);

    expect(service.activePool()).toEqual(mockPool);
    expect(service.myVotesUsed()).toBe(1);
    expect(service.myVotesRemaining()).toBe(2);
  });

  it('loadPool handles 204 No Content gracefully', () => {
    service.loadPool('room-1', true).subscribe((res) => {
      expect(res).toBeNull();
    });

    const req = httpMock.expectOne('/api/rooms/room-1/vote-pools/active?includeLatestClosed=true');
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(service.activePool()).toBeNull();
  });

  it('createPool posts new vote pool configuration', () => {
    const createReq: CreateVotePoolRequest = {
      title: 'Roadmap Q1',
      votesPerParticipant: 5,
      allowMultipleVotesPerIdea: false,
    };
    const mockCreated: VotePool = {
      id: 'pool-2',
      roomId: 'room-1',
      title: 'Roadmap Q1',
      votesPerParticipant: 5,
      allowMultipleVotesPerIdea: false,
      status: 'OPEN',
      createdById: 1,
      createdAt: '2026-11-21T00:00:00Z',
      myVotesUsed: 0,
      myVotesRemaining: 5,
      myVotesByIdeaId: {},
      tallies: [],
    };

    service.createPool('room-1', createReq).subscribe((pool) => {
      expect(pool).toEqual(mockCreated);
    });

    const req = httpMock.expectOne('/api/rooms/room-1/vote-pools');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(createReq);
    req.flush(mockCreated);

    expect(service.activePool()).toEqual(mockCreated);
    expect(service.myVotesRemaining()).toBe(5);
  });

  it('castVote sends ideaId to pool votes endpoint and updates pool', () => {
    const mockUpdated: VotePool = {
      id: 'pool-1',
      roomId: 'room-1',
      title: 'Pool',
      votesPerParticipant: 3,
      allowMultipleVotesPerIdea: true,
      status: 'OPEN',
      createdById: 1,
      createdAt: '2026-11-21T00:00:00Z',
      myVotesUsed: 1,
      myVotesRemaining: 2,
      myVotesByIdeaId: { 'idea-1': 1 },
      tallies: [{ ideaId: 'idea-1', ideaTitle: 'Idea 1', poolVotes: 1 }],
    };

    service.castVote('room-1', 'pool-1', 'idea-1').subscribe((pool) => {
      expect(pool).toEqual(mockUpdated);
    });

    const req = httpMock.expectOne('/api/rooms/room-1/vote-pools/pool-1/votes');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ ideaId: 'idea-1' });
    req.flush(mockUpdated);

    expect(service.activePool()?.myVotesUsed).toBe(1);
  });

  it('removeVote deletes vote on idea and frees participant budget', () => {
    const mockUpdated: VotePool = {
      id: 'pool-1',
      roomId: 'room-1',
      title: 'Pool',
      votesPerParticipant: 3,
      allowMultipleVotesPerIdea: true,
      status: 'OPEN',
      createdById: 1,
      createdAt: '2026-11-21T00:00:00Z',
      myVotesUsed: 0,
      myVotesRemaining: 3,
      myVotesByIdeaId: {},
      tallies: [],
    };

    service.removeVote('room-1', 'pool-1', 'idea-1').subscribe((pool) => {
      expect(pool).toEqual(mockUpdated);
    });

    const req = httpMock.expectOne('/api/rooms/room-1/vote-pools/pool-1/votes/idea-1');
    expect(req.request.method).toBe('DELETE');
    req.flush(mockUpdated);

    expect(service.activePool()?.myVotesRemaining).toBe(3);
  });

  it('closePool closes active pool and updates latestClosedPool', () => {
    const mockClosed: VotePool = {
      id: 'pool-1',
      roomId: 'room-1',
      title: 'Pool',
      votesPerParticipant: 3,
      allowMultipleVotesPerIdea: true,
      status: 'CLOSED',
      closedAt: '2026-11-21T01:00:00Z',
      createdById: 1,
      createdAt: '2026-11-21T00:00:00Z',
      myVotesUsed: 2,
      myVotesRemaining: 1,
      myVotesByIdeaId: { 'idea-1': 2 },
      tallies: [{ ideaId: 'idea-1', ideaTitle: 'Auth Flow', poolVotes: 5 }],
    };

    service.closePool('room-1', 'pool-1').subscribe((pool) => {
      expect(pool.status).toBe('CLOSED');
    });

    const req = httpMock.expectOne('/api/rooms/room-1/vote-pools/pool-1/close');
    expect(req.request.method).toBe('POST');
    req.flush(mockClosed);

    expect(service.activePool()).toBeNull();
    expect(service.latestClosedPool()).toEqual(mockClosed);
  });

  it('reset clears all pool signals and errors', () => {
    service.reset();
    expect(service.activePool()).toBeNull();
    expect(service.latestClosedPool()).toBeNull();
    expect(service.error()).toBeNull();
    expect(service.loading()).toBe(false);
  });
});
