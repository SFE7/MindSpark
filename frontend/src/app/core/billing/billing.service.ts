import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, finalize, forkJoin, map, of, tap, throwError } from 'rxjs';
import {
  BillingRecord,
  CheckoutConfirmResponse,
  CheckoutResponse,
  PaymentConfigResponse,
  Plan,
  RoomUsageResponse,
  SubscribeRequest,
  SubscriptionMeResponse,
  UpgradeRequest,
} from './billing.models';
import { KeycloakAuth } from '../auth/keycloak-auth';
import { AiService } from '../ai/ai.service';

@Injectable({ providedIn: 'root' })
export class BillingService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(KeycloakAuth);
  private readonly aiService = inject(AiService);

  readonly plans = signal<Plan[]>([]);
  readonly subscription = signal<SubscriptionMeResponse | null>(null);
  readonly activeRoomsCount = signal<number>(0);
  readonly billingHistory = signal<BillingRecord[]>([]);
  readonly paymentConfig = signal<PaymentConfigResponse | null>(null);
  readonly loading = signal<boolean>(false);
  readonly error = signal<string | null>(null);

  /**
   * Load plans, active subscription, room usage, billing history, and payment config in parallel.
   */
  loadAll(): Observable<{
    plans: Plan[];
    subscription: SubscriptionMeResponse;
    usage: RoomUsageResponse;
    history: BillingRecord[];
    paymentConfig: PaymentConfigResponse;
  }> {
    this.loading.set(true);
    this.error.set(null);

    return forkJoin({
      plans: this.http.get<Plan[]>('/api/plans').pipe(catchError(() => of([]))),
      subscription: this.http.get<SubscriptionMeResponse>('/api/subscriptions/me').pipe(
        catchError(() =>
          of({
            planCode: 'FREE',
            planName: 'Free',
            status: 'ACTIVE',
            startedAt: null,
            currentPeriodStart: null,
            currentPeriodEnd: null,
            canceledAt: null,
            maxActiveRooms: 3,
            maxParticipantsPerRoom: 5,
            aiDailyLimit: 10,
            aiTier: 'FREE',
          })
        )
      ),
      usage: this.http.get<RoomUsageResponse>('/api/rooms/usage').pipe(catchError(() => of({ activeRoomsCount: 0 }))),
      history: this.http.get<BillingRecord[]>('/api/billing/history').pipe(catchError(() => of([]))),
      paymentConfig: this.getPaymentConfig().pipe(
        catchError(() => of({ mode: 'SIMULATED', provider: 'SIMULATED', availablePlans: ['PREMIUM', 'TEAM'] }))
      ),
    }).pipe(
      tap(({ plans, subscription, usage, history, paymentConfig }) => {
        this.plans.set(plans);
        this.subscription.set(subscription);
        this.activeRoomsCount.set(usage.activeRoomsCount);
        this.billingHistory.set(history);
        this.paymentConfig.set(paymentConfig);
      }),
      catchError(err => {
        const msg = this.extractErrorMessage(err);
        this.error.set(msg);
        return throwError(() => err);
      }),
      finalize(() => {
        this.loading.set(false);
      })
    );
  }

  /**
   * Retrieve payment configuration (Polar Sandbox vs Simulated mode).
   */
  getPaymentConfig(): Observable<PaymentConfigResponse> {
    return this.http.get<PaymentConfigResponse>('/api/subscriptions/payment-config').pipe(
      tap(config => this.paymentConfig.set(config)),
      catchError(() => {
        const fallback: PaymentConfigResponse = {
          mode: 'SIMULATED',
          provider: 'SIMULATED',
          availablePlans: ['PREMIUM', 'TEAM'],
        };
        this.paymentConfig.set(fallback);
        return of(fallback);
      })
    );
  }

  /**
   * Initiate checkout for a paid plan. Returns Polar checkout URL in sandbox mode.
   */
  startCheckout(planCode: string): Observable<CheckoutResponse> {
    this.loading.set(true);
    this.error.set(null);
    const returnOrigin = typeof window !== 'undefined' ? window.location.origin : 'http://localhost:4200';

    return this.http.post<CheckoutResponse>('/api/subscriptions/checkout', { planCode, returnOrigin }).pipe(
      catchError(err => {
        const msg = this.extractErrorMessage(err);
        this.error.set(msg);
        return throwError(() => err);
      }),
      finalize(() => {
        this.loading.set(false);
      })
    );
  }

  /**
   * Verify and confirm a Polar checkout server-to-server.
   */
  confirmCheckout(checkoutId: string): Observable<CheckoutConfirmResponse> {
    this.loading.set(true);
    this.error.set(null);

    return this.http.post<CheckoutConfirmResponse>('/api/subscriptions/checkout/confirm', { checkoutId }).pipe(
      tap(async res => {
        if (res.status === 'ACTIVATED' && res.subscription) {
          this.subscription.set(res.subscription);
          await this.postActionSync();
        }
      }),
      catchError(err => {
        const msg = this.extractErrorMessage(err);
        this.error.set(msg);
        return throwError(() => err);
      }),
      finalize(() => {
        this.loading.set(false);
      })
    );
  }

  /**
   * Subscribe to a paid plan.
   */
  subscribe(planCode: string, paymentMethodLabel?: string): Observable<SubscriptionMeResponse> {
    this.loading.set(true);
    this.error.set(null);

    const req: SubscribeRequest = { planCode, paymentMethodLabel };
    return this.http.post<SubscriptionMeResponse>('/api/subscriptions/subscribe', req).pipe(
      tap(async sub => {
        this.subscription.set(sub);
        await this.postActionSync();
      }),
      catchError(err => {
        const msg = this.extractErrorMessage(err);
        this.error.set(msg);
        return throwError(() => err);
      }),
      finalize(() => {
        this.loading.set(false);
      })
    );
  }

  /**
   * Upgrade to a higher plan (PREMIUM -> TEAM, etc.).
   */
  upgrade(targetPlanCode: string, paymentMethodLabel?: string): Observable<SubscriptionMeResponse> {
    this.loading.set(true);
    this.error.set(null);

    const req: UpgradeRequest = { targetPlanCode, paymentMethodLabel };
    return this.http.post<SubscriptionMeResponse>('/api/subscriptions/upgrade', req).pipe(
      tap(async sub => {
        this.subscription.set(sub);
        await this.postActionSync();
      }),
      catchError(err => {
        const msg = this.extractErrorMessage(err);
        this.error.set(msg);
        return throwError(() => err);
      }),
      finalize(() => {
        this.loading.set(false);
      })
    );
  }

  /**
   * Cancel active paid subscription (immediate downgrade to FREE).
   */
  cancel(): Observable<SubscriptionMeResponse> {
    this.loading.set(true);
    this.error.set(null);

    return this.http.post<SubscriptionMeResponse>('/api/subscriptions/cancel', {}).pipe(
      tap(async sub => {
        this.subscription.set(sub);
        await this.postActionSync();
      }),
      catchError(err => {
        const msg = this.extractErrorMessage(err);
        this.error.set(msg);
        return throwError(() => err);
      }),
      finalize(() => {
        this.loading.set(false);
      })
    );
  }

  /**
   * Post-action synchronization:
   * 1. Force refresh Keycloak token so updated realm role is in JWT
   * 2. Reload AI user & usage so drawer immediately reflects new tier & limits
   * 3. Refresh plans, active subscription, and billing history
   */
  private async postActionSync(): Promise<void> {
    try {
      await this.auth.refreshToken();
    } catch {
      // Ignore if refresh throws
    }

    try {
      this.aiService.loadUserAndUsage().subscribe({
        error: () => {
          // Non-blocking
        },
      });
    } catch {
      // Ignore
    }

    this.loadAll().subscribe();
  }

  private extractErrorMessage(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      if (err.error?.message) {
        return err.error.message;
      }
      if (typeof err.error === 'string') {
        return err.error;
      }
      return `Request failed with status ${err.status}`;
    }
    return err instanceof Error ? err.message : 'An unexpected error occurred.';
  }
}
