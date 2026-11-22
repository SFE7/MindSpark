import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AiService, normalizeAiError } from './ai.service';
import { HttpErrorResponse } from '@angular/common/http';
import {
  AiResponse,
  GenerateIdeasResult,
  ImproveIdeaResult,
  GroupIdeasResult,
  ActionPlanResult,
  FindRisksResult,
  CompareIdeasResult,
  SummarizeSessionResult,
} from './ai.models';

describe('AiService', () => {
  let service: AiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), AiService],
    });
    service = TestBed.inject(AiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('initializes with null state and not loading', () => {
    expect(service.tier()).toBeNull();
    expect(service.usage()).toBeNull();
    expect(service.loading()).toBe(false);
    expect(service.currentError()).toBeNull();
  });

  describe('Lazy loading: loadUserAndUsage', () => {
    it('fetches /api/ai/me and /api/ai/usage and updates signals', () => {
      service.loadUserAndUsage().subscribe(res => {
        expect(res.user.tier).toBe('FREE');
        expect(res.usage.remainingToday).toBe(7);
        expect(service.tier()).toBe('FREE');
        expect(service.usage()?.usedToday).toBe(3);
      });

      const meReq = httpTesting.expectOne('/api/ai/me');
      expect(meReq.request.method).toBe('GET');
      meReq.flush({ userId: 'u123', tier: 'FREE' });

      const usageReq = httpTesting.expectOne('/api/ai/usage');
      expect(usageReq.request.method).toBe('GET');
      usageReq.flush({
        tier: 'FREE',
        dailyLimit: 10,
        usedToday: 3,
        remainingToday: 7,
        resetsAtUtc: '2026-11-20T00:00:00Z',
      });
    });
  });

  describe('Feature Endpoints & Envelope Unwrapping', () => {
    it('POST /api/ai/ideas/generate returns envelope and refreshes usage', () => {
      const mockResult: AiResponse<GenerateIdeasResult> = {
        result: { ideas: [{ title: 'Idea 1', description: 'Desc 1' }] },
        meta: { model: 'gemini-3.5-flash-lite', tier: 'FREE' },
      };

      service.generateIdeas({ topic: 'Test Topic', count: 3 }).subscribe(res => {
        expect(res.result.ideas.length).toBe(1);
        expect(res.meta.model).toBe('gemini-3.5-flash-lite');
      });

      const req = httpTesting.expectOne('/api/ai/ideas/generate');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ topic: 'Test Topic', count: 3 });
      req.flush(mockResult);

      // Auto-refresh usage request
      const usageReq = httpTesting.expectOne('/api/ai/usage');
      usageReq.flush({
        tier: 'FREE',
        dailyLimit: 10,
        usedToday: 4,
        remainingToday: 6,
        resetsAtUtc: '2026-11-20T00:00:00Z',
      });

      expect(service.usage()?.remainingToday).toBe(6);
      expect(service.loading()).toBe(false);
    });

    it('POST /api/ai/ideas/improve', () => {
      const mockResult: AiResponse<ImproveIdeaResult> = {
        result: { title: 'Better Idea', text: 'Detailed text', changes: ['Clearer'] },
        meta: { model: 'gemini-3.5-flash-lite', tier: 'FREE' },
      };

      service.improveIdea({ idea: { id: '1', text: 'Raw idea' }, mode: 'improve' }).subscribe(res => {
        expect(res.result.title).toBe('Better Idea');
      });

      const req = httpTesting.expectOne('/api/ai/ideas/improve');
      expect(req.request.method).toBe('POST');
      req.flush(mockResult);

      const usageReq = httpTesting.expectOne('/api/ai/usage');
      usageReq.flush({ tier: 'FREE', dailyLimit: 10, usedToday: 5, remainingToday: 5, resetsAtUtc: '' });
    });

    it('POST /api/ai/ideas/group', () => {
      const mockResult: AiResponse<GroupIdeasResult> = {
        result: { groups: [{ name: 'Group A', ideaIds: ['1', '2'], rationale: 'Reason' }], ungroupedIds: [] },
        meta: { model: 'gemini-3.5-flash-lite', tier: 'FREE' },
      };

      service.groupIdeas({ ideas: [{ id: '1', text: 'A' }, { id: '2', text: 'B' }] }).subscribe(res => {
        expect(res.result.groups.length).toBe(1);
      });

      const req = httpTesting.expectOne('/api/ai/ideas/group');
      expect(req.request.method).toBe('POST');
      req.flush(mockResult);

      const usageReq = httpTesting.expectOne('/api/ai/usage');
      usageReq.flush({ tier: 'FREE', dailyLimit: 10, usedToday: 6, remainingToday: 4, resetsAtUtc: '' });
    });

    it('POST /api/ai/ideas/action-plan', () => {
      const mockResult: AiResponse<ActionPlanResult> = {
        result: {
          title: 'Action Plan',
          steps: [{ order: 1, title: 'Step 1', description: 'Do it', priority: 'high', relatedIdeaIds: ['1'] }],
          quickWins: ['Win 1'],
        },
        meta: { model: 'gemini-3.5-flash-lite', tier: 'FREE' },
      };

      service.actionPlan({ goal: 'Launch', ideas: [{ id: '1', text: 'Idea 1' }] }).subscribe(res => {
        expect(res.result.steps[0].priority).toBe('high');
      });

      const req = httpTesting.expectOne('/api/ai/ideas/action-plan');
      expect(req.request.method).toBe('POST');
      req.flush(mockResult);

      const usageReq = httpTesting.expectOne('/api/ai/usage');
      usageReq.flush({ tier: 'FREE', dailyLimit: 10, usedToday: 7, remainingToday: 3, resetsAtUtc: '' });
    });

    it('POST /api/ai/ideas/risks', () => {
      const mockResult: AiResponse<FindRisksResult> = {
        result: { risks: [{ title: 'Risk 1', description: 'Desc', severity: 'high', mitigation: 'Mitigate' }] },
        meta: { model: 'gemini-3.5-flash-lite', tier: 'FREE' },
      };

      service.findRisks({ idea: { id: '1', text: 'Idea 1' } }).subscribe(res => {
        expect(res.result.risks.length).toBe(1);
      });

      const req = httpTesting.expectOne('/api/ai/ideas/risks');
      expect(req.request.method).toBe('POST');
      req.flush(mockResult);

      const usageReq = httpTesting.expectOne('/api/ai/usage');
      usageReq.flush({ tier: 'FREE', dailyLimit: 10, usedToday: 8, remainingToday: 2, resetsAtUtc: '' });
    });

    it('POST /api/ai/ideas/compare', () => {
      const mockResult: AiResponse<CompareIdeasResult> = {
        result: {
          criteria: ['Feasibility'],
          comparison: [{ ideaId: '1', scores: { Feasibility: 9 }, strengths: ['Fast'], weaknesses: [] }],
          recommendation: { ideaId: '1', reason: 'Best idea' },
        },
        meta: { model: 'gemini-3.5-flash-lite', tier: 'FREE' },
      };

      service.compareIdeas({ ideas: [{ id: '1', text: 'A' }, { id: '2', text: 'B' }] }).subscribe(res => {
        expect(res.result.recommendation.ideaId).toBe('1');
      });

      const req = httpTesting.expectOne('/api/ai/ideas/compare');
      expect(req.request.method).toBe('POST');
      req.flush(mockResult);

      const usageReq = httpTesting.expectOne('/api/ai/usage');
      usageReq.flush({ tier: 'FREE', dailyLimit: 10, usedToday: 9, remainingToday: 1, resetsAtUtc: '' });
    });

    it('POST /api/ai/sessions/summarize', () => {
      const mockResult: AiResponse<SummarizeSessionResult> = {
        result: {
          summary: 'Session summary text',
          themes: ['AI', 'Collaboration'],
          topIdeas: [{ id: '1', reason: 'High impact' }],
          openQuestions: ['How to scale?'],
        },
        meta: { model: 'gemini-3.5-flash-lite', tier: 'FREE' },
      };

      service.summarizeSession({ ideas: [{ id: '1', text: 'Idea' }] }).subscribe(res => {
        expect(res.result.summary).toBe('Session summary text');
      });

      const req = httpTesting.expectOne('/api/ai/sessions/summarize');
      expect(req.request.method).toBe('POST');
      req.flush(mockResult);

      const usageReq = httpTesting.expectOne('/api/ai/usage');
      usageReq.flush({ tier: 'FREE', dailyLimit: 10, usedToday: 10, remainingToday: 0, resetsAtUtc: '' });
    });
  });

  describe('Error Mapping & Quota Management', () => {
    it('maps 400 VALIDATION_ERROR to non-retryable inline validation error', () => {
      service.generateIdeas({ topic: '' }).subscribe({
        error: err => {
          expect(err.code).toBe('VALIDATION_ERROR');
          expect(err.canRetry).toBe(false);
          expect(err.message).toBe('Topic is required');
        },
      });

      const req = httpTesting.expectOne('/api/ai/ideas/generate');
      req.flush(
        { error: 'VALIDATION_ERROR', message: 'Topic is required' },
        { status: 400, statusText: 'Bad Request' }
      );

      const usageReq = httpTesting.expectOne('/api/ai/usage');
      usageReq.flush({ tier: 'FREE', dailyLimit: 10, usedToday: 3, remainingToday: 7, resetsAtUtc: '' });

      expect(service.currentError()?.code).toBe('VALIDATION_ERROR');
    });

    it('maps 429 DAILY_LIMIT_REACHED to quota reached error banner', () => {
      service.generateIdeas({ topic: 'Topic' }).subscribe({
        error: err => {
          expect(err.code).toBe('DAILY_LIMIT_REACHED');
          expect(err.canRetry).toBe(false);
          expect(err.message).toContain('Daily request limit');
        },
      });

      const req = httpTesting.expectOne('/api/ai/ideas/generate');
      req.flush(
        { error: 'DAILY_LIMIT_REACHED', message: 'Daily request limit reached' },
        { status: 429, statusText: 'Too Many Requests' }
      );

      const usageReq = httpTesting.expectOne('/api/ai/usage');
      usageReq.flush({ tier: 'FREE', dailyLimit: 10, usedToday: 10, remainingToday: 0, resetsAtUtc: '' });

      expect(service.currentError()?.code).toBe('DAILY_LIMIT_REACHED');
    });

    it('maps 429 PROVIDER_BUSY with retryAfterSeconds', () => {
      service.generateIdeas({ topic: 'Topic' }).subscribe({
        error: err => {
          expect(err.code).toBe('PROVIDER_BUSY');
          expect(err.canRetry).toBe(true);
          expect(err.retryAfterSeconds).toBe(45);
        },
      });

      const req = httpTesting.expectOne('/api/ai/ideas/generate');
      req.flush(
        { error: 'PROVIDER_BUSY', message: 'Provider busy', retryAfterSeconds: 45 },
        { status: 429, statusText: 'Too Many Requests' }
      );

      const usageReq = httpTesting.expectOne('/api/ai/usage');
      usageReq.flush({ tier: 'FREE', dailyLimit: 10, usedToday: 5, remainingToday: 5, resetsAtUtc: '' });

      expect(service.currentError()?.retryAfterSeconds).toBe(45);
    });

    it('maps 502 BAD_MODEL_OUTPUT to retryable format error', () => {
      service.generateIdeas({ topic: 'Topic' }).subscribe({
        error: err => {
          expect(err.code).toBe('BAD_MODEL_OUTPUT');
          expect(err.canRetry).toBe(true);
        },
      });

      const req = httpTesting.expectOne('/api/ai/ideas/generate');
      req.flush(
        { error: 'BAD_MODEL_OUTPUT', message: 'Unparseable response' },
        { status: 502, statusText: 'Bad Gateway' }
      );

      const usageReq = httpTesting.expectOne('/api/ai/usage');
      usageReq.flush({ tier: 'FREE', dailyLimit: 10, usedToday: 5, remainingToday: 5, resetsAtUtc: '' });

      expect(service.currentError()?.code).toBe('BAD_MODEL_OUTPUT');
    });

    it('maps 503 AI_UNAVAILABLE to retryable temporary unavailable error', () => {
      service.generateIdeas({ topic: 'Topic' }).subscribe({
        error: err => {
          expect(err.code).toBe('AI_UNAVAILABLE');
          expect(err.canRetry).toBe(true);
        },
      });

      const req = httpTesting.expectOne('/api/ai/ideas/generate');
      req.flush(
        { error: 'AI_UNAVAILABLE', message: 'Service unavailable' },
        { status: 503, statusText: 'Service Unavailable' }
      );

      const usageReq = httpTesting.expectOne('/api/ai/usage');
      usageReq.flush({ tier: 'FREE', dailyLimit: 10, usedToday: 5, remainingToday: 5, resetsAtUtc: '' });

      expect(service.currentError()?.code).toBe('AI_UNAVAILABLE');
    });
  });

  describe('normalizeAiError helper function', () => {
    it('handles non-HTTP errors gracefully', () => {
      const err = new Error('Network timeout');
      const normalized = normalizeAiError(err);
      expect(normalized.code).toBe('UNKNOWN_ERROR');
      expect(normalized.message).toBe('Network timeout');
      expect(normalized.canRetry).toBe(true);
    });

    it('defaults retryAfterSeconds to 60 if omitted in PROVIDER_BUSY', () => {
      const httpErr = new HttpErrorResponse({
        error: { error: 'PROVIDER_BUSY', message: 'Busy' },
        status: 429,
      });
      const normalized = normalizeAiError(httpErr);
      expect(normalized.code).toBe('PROVIDER_BUSY');
      expect(normalized.retryAfterSeconds).toBe(60);
    });
  });
});
