import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, finalize, forkJoin, map, of, tap, throwError } from 'rxjs';
import {
  AiErrorBody,
  AiErrorCode,
  AiResponse,
  AiTier,
  AiUsageResponse,
  AiUserResponse,
  ActionPlanRequest,
  ActionPlanResult,
  CompareIdeasRequest,
  CompareIdeasResult,
  FindRisksRequest,
  FindRisksResult,
  GenerateIdeasRequest,
  GenerateIdeasResult,
  GroupIdeasRequest,
  GroupIdeasResult,
  ImproveIdeaRequest,
  ImproveIdeaResult,
  NormalizedAiError,
  SummarizeSessionRequest,
  SummarizeSessionResult,
} from './ai.models';

export function normalizeAiError(err: unknown): NormalizedAiError {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as Partial<AiErrorBody> | null;
    const backendCode = body?.error;
    const backendMessage = body?.message;
    const retryAfter = typeof body?.retryAfterSeconds === 'number' ? body.retryAfterSeconds : undefined;

    if (err.status === 400 || backendCode === 'VALIDATION_ERROR') {
      return {
        code: 'VALIDATION_ERROR',
        message: backendMessage || 'Invalid request parameters.',
        canRetry: false,
      };
    }

    if (err.status === 429) {
      if (backendCode === 'DAILY_LIMIT_REACHED') {
        return {
          code: 'DAILY_LIMIT_REACHED',
          message: backendMessage || 'Daily AI request limit reached. Resets at midnight UTC.',
          canRetry: false,
        };
      }
      return {
        code: 'PROVIDER_BUSY',
        message: backendMessage || 'The AI provider is currently busy. Please try again soon.',
        retryAfterSeconds: retryAfter ?? 60,
        canRetry: true,
      };
    }

    if (err.status === 502 || backendCode === 'BAD_MODEL_OUTPUT') {
      return {
        code: 'BAD_MODEL_OUTPUT',
        message: backendMessage || 'The AI model returned an unparseable response. Please try again.',
        canRetry: true,
      };
    }

    if (err.status === 503 || backendCode === 'AI_UNAVAILABLE') {
      return {
        code: 'AI_UNAVAILABLE',
        message: backendMessage || 'The AI service is temporarily unavailable. Please try again later.',
        canRetry: true,
      };
    }

    return {
      code: 'UNKNOWN_ERROR',
      message: backendMessage || err.message || 'An unexpected error occurred while processing your AI request.',
      canRetry: true,
    };
  }

  return {
    code: 'UNKNOWN_ERROR',
    message: err instanceof Error ? err.message : 'An unexpected error occurred.',
    canRetry: true,
  };
}

@Injectable({ providedIn: 'root' })
export class AiService {
  private readonly http = inject(HttpClient);

  readonly tier = signal<AiTier | null>(null);
  readonly usage = signal<AiUsageResponse | null>(null);
  readonly loading = signal<boolean>(false);
  readonly currentError = signal<NormalizedAiError | null>(null);

  /**
   * Lazy load me and usage on demand (e.g. when opening AI drawer).
   */
  loadUserAndUsage(): Observable<{ user: AiUserResponse; usage: AiUsageResponse }> {
    this.currentError.set(null);
    return forkJoin({
      user: this.http.get<AiUserResponse>('/api/ai/me'),
      usage: this.http.get<AiUsageResponse>('/api/ai/usage'),
    }).pipe(
      tap(({ user, usage }) => {
        this.tier.set(user.tier || usage.tier);
        this.usage.set(usage);
      }),
      catchError(err => {
        const normalized = normalizeAiError(err);
        this.currentError.set(normalized);
        return throwError(() => normalized);
      })
    );
  }

  /**
   * Refreshes the daily quota usage counters.
   */
  refreshUsage(): Observable<AiUsageResponse> {
    return this.http.get<AiUsageResponse>('/api/ai/usage').pipe(
      tap(usage => {
        this.usage.set(usage);
        if (usage.tier) {
          this.tier.set(usage.tier);
        }
      }),
      catchError(err => {
        // Silent fail or non-blocking for background refresh
        return of(this.usage()!);
      })
    );
  }

  clearError(): void {
    this.currentError.set(null);
  }

  /**
   * 1. Generate brainstorm ideas for a topic.
   */
  generateIdeas(request: GenerateIdeasRequest): Observable<AiResponse<GenerateIdeasResult>> {
    return this.wrapRequest(() =>
      this.http.post<AiResponse<GenerateIdeasResult>>('/api/ai/ideas/generate', request)
    );
  }

  /**
   * 2. Improve or expand an existing idea.
   */
  improveIdea(request: ImproveIdeaRequest): Observable<AiResponse<ImproveIdeaResult>> {
    return this.wrapRequest(() =>
      this.http.post<AiResponse<ImproveIdeaResult>>('/api/ai/ideas/improve', request)
    );
  }

  /**
   * 3. Group multiple ideas thematically.
   */
  groupIdeas(request: GroupIdeasRequest): Observable<AiResponse<GroupIdeasResult>> {
    return this.wrapRequest(() =>
      this.http.post<AiResponse<GroupIdeasResult>>('/api/ai/ideas/group', request)
    );
  }

  /**
   * 4. Build an action plan from ideas.
   */
  actionPlan(request: ActionPlanRequest): Observable<AiResponse<ActionPlanResult>> {
    return this.wrapRequest(() =>
      this.http.post<AiResponse<ActionPlanResult>>('/api/ai/ideas/action-plan', request)
    );
  }

  /**
   * 5. Find potential risks and mitigations for an idea.
   */
  findRisks(request: FindRisksRequest): Observable<AiResponse<FindRisksResult>> {
    return this.wrapRequest(() =>
      this.http.post<AiResponse<FindRisksResult>>('/api/ai/ideas/risks', request)
    );
  }

  /**
   * 6. Compare 2 to 4 ideas against evaluation criteria.
   */
  compareIdeas(request: CompareIdeasRequest): Observable<AiResponse<CompareIdeasResult>> {
    return this.wrapRequest(() =>
      this.http.post<AiResponse<CompareIdeasResult>>('/api/ai/ideas/compare', request)
    );
  }

  /**
   * 7. Summarize an entire room/session of ideas.
   */
  summarizeSession(request: SummarizeSessionRequest): Observable<AiResponse<SummarizeSessionResult>> {
    return this.wrapRequest(() =>
      this.http.post<AiResponse<SummarizeSessionResult>>('/api/ai/sessions/summarize', request)
    );
  }

  private wrapRequest<T>(req: () => Observable<AiResponse<T>>): Observable<AiResponse<T>> {
    this.loading.set(true);
    this.currentError.set(null);

    return req().pipe(
      tap(() => {
        // Auto-refresh quota upon completed AI response
        this.refreshUsage().subscribe();
      }),
      catchError(err => {
        const normalized = normalizeAiError(err);
        this.currentError.set(normalized);
        // Refresh usage on error in case quota was deducted or refunded
        this.refreshUsage().subscribe();
        return throwError(() => normalized);
      }),
      finalize(() => {
        this.loading.set(false);
      })
    );
  }
}
