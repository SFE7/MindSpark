import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable, catchError, finalize, forkJoin, of, tap, throwError } from 'rxjs';
import {
  AdminAssignRequest,
  AdminCancelResponse,
  AdminListResponse,
  AdminPlan,
  AdminSubscription,
  AdminSubscriptionsPage,
  AdminUserDirectory,
  AdminUserResponse,
  AiStats,
  BillingStats,
  CreateAdminRequest,
  CreatePlanRequest,
  PlatformStats,
  UpdatePlanRequest,
} from './admin.models';

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);

  readonly platformStats = signal<PlatformStats | null>(null);
  readonly billingStats = signal<BillingStats | null>(null);
  readonly aiStats = signal<AiStats | null>(null);

  readonly platformError = signal<string | null>(null);
  readonly billingError = signal<string | null>(null);
  readonly aiError = signal<string | null>(null);

  readonly overviewLoading = signal<boolean>(false);
  readonly plans = signal<AdminPlan[]>([]);
  readonly plansLoading = signal<boolean>(false);
  readonly subscriptionsPage = signal<AdminSubscriptionsPage | null>(null);
  readonly subscriptionsLoading = signal<boolean>(false);
  readonly usersDirectory = signal<AdminUserDirectory[]>([]);
  readonly directoryLoading = signal<boolean>(false);
  readonly adminsList = signal<AdminListResponse | null>(null);
  readonly adminsLoading = signal<boolean>(false);

  /**
   * Resilient parallel loading: Each source catches its own error independently.
   * A failure in one service does not blank out healthy metrics from the other two.
   */
  loadOverviewStats(): Observable<{
    platform: PlatformStats | null;
    billing: BillingStats | null;
    ai: AiStats | null;
  }> {
    this.overviewLoading.set(true);
    this.platformError.set(null);
    this.billingError.set(null);
    this.aiError.set(null);

    return forkJoin({
      platform: this.http.get<PlatformStats>('/api/admin/stats/platform').pipe(
        catchError(err => {
          this.platformError.set(this.extractErrorMessage(err));
          return of(null);
        })
      ),
      billing: this.http.get<BillingStats>('/api/admin/stats/billing').pipe(
        catchError(err => {
          this.billingError.set(this.extractErrorMessage(err));
          return of(null);
        })
      ),
      ai: this.http.get<AiStats>('/api/admin/stats/ai').pipe(
        catchError(err => {
          this.aiError.set(this.extractErrorMessage(err));
          return of(null);
        })
      ),
    }).pipe(
      tap(({ platform, billing, ai }) => {
        if (platform) this.platformStats.set(platform);
        if (billing) this.billingStats.set(billing);
        if (ai) this.aiStats.set(ai);
      }),
      finalize(() => {
        this.overviewLoading.set(false);
      })
    );
  }

  loadPlans(): Observable<AdminPlan[]> {
    this.plansLoading.set(true);
    return this.http.get<AdminPlan[]>('/api/admin/plans').pipe(
      tap(data => this.plans.set(data)),
      finalize(() => this.plansLoading.set(false))
    );
  }

  createPlan(req: CreatePlanRequest): Observable<AdminPlan> {
    return this.http.post<AdminPlan>('/api/admin/plans', req).pipe(
      tap(() => this.loadPlans().subscribe())
    );
  }

  updatePlan(id: string, req: UpdatePlanRequest): Observable<AdminPlan> {
    return this.http.put<AdminPlan>(`/api/admin/plans/${id}`, req).pipe(
      tap(() => this.loadPlans().subscribe())
    );
  }

  togglePlanStatus(id: string, active?: boolean): Observable<AdminPlan> {
    const body = active !== undefined ? { active } : {};
    return this.http.patch<AdminPlan>(`/api/admin/plans/${id}/status`, body).pipe(
      tap(() => this.loadPlans().subscribe())
    );
  }

  loadUsersDirectory(): Observable<AdminUserDirectory[]> {
    this.directoryLoading.set(true);
    return this.http.get<AdminUserDirectory[]>('/api/admin/users').pipe(
      tap(data => this.usersDirectory.set(data)),
      finalize(() => this.directoryLoading.set(false))
    );
  }

  loadSubscriptions(filters: {
    status?: string;
    planCode?: string;
    userId?: string;
    page?: number;
    size?: number;
  } = {}): Observable<AdminSubscriptionsPage> {
    this.subscriptionsLoading.set(true);
    let params = new HttpParams();
    if (filters.status) params = params.set('status', filters.status);
    if (filters.planCode) params = params.set('planCode', filters.planCode);
    if (filters.userId) params = params.set('userId', filters.userId);
    if (filters.page !== undefined) params = params.set('page', filters.page.toString());
    if (filters.size !== undefined) params = params.set('size', filters.size.toString());

    return this.http.get<AdminSubscriptionsPage>('/api/admin/subscriptions', { params }).pipe(
      tap(data => this.subscriptionsPage.set(data)),
      finalize(() => this.subscriptionsLoading.set(false))
    );
  }

  assignSubscription(req: AdminAssignRequest): Observable<AdminSubscription> {
    return this.http.post<AdminSubscription>('/api/admin/subscriptions/assign', req);
  }

  cancelSubscription(userId: string): Observable<AdminCancelResponse> {
    return this.http.post<AdminCancelResponse>(`/api/admin/subscriptions/${encodeURIComponent(userId)}/cancel`, {});
  }

  loadAdmins(): Observable<AdminListResponse> {
    this.adminsLoading.set(true);
    return this.http.get<AdminListResponse>('/api/admin/admins').pipe(
      tap(data => this.adminsList.set(data)),
      finalize(() => this.adminsLoading.set(false))
    );
  }

  createAdmin(req: CreateAdminRequest): Observable<AdminUserResponse> {
    return this.http.post<AdminUserResponse>('/api/admin/admins', req);
  }

  private extractErrorMessage(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      if (err.error && typeof err.error === 'object' && 'message' in err.error) {
        return String((err.error as { message: unknown }).message);
      }
      if (err.status === 403) return 'Admin access required (403 Forbidden).';
      if (err.status === 401) return 'Session expired or unauthenticated (401).';
      if (err.status === 503) return 'Service temporarily unavailable (503).';
      if (err.status === 502) return 'Bad gateway (502).';
      return `HTTP error ${err.status}: ${err.statusText}`;
    }
    return 'An unexpected error occurred.';
  }
}
