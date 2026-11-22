import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { BillingService } from '../../../../core/billing/billing.service';
import { Plan } from '../../../../core/billing/billing.models';
import { KeycloakAuth } from '../../../../core/auth/keycloak-auth';

@Component({
  selector: 'app-billing-page',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './billing-page.html',
  styleUrl: './billing-page.scss',
})
export class BillingPageComponent implements OnInit {
  protected readonly billingService = inject(BillingService);
  protected readonly auth = inject(KeycloakAuth);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly plans = this.billingService.plans;
  protected readonly subscription = this.billingService.subscription;
  protected readonly activeRoomsCount = this.billingService.activeRoomsCount;
  protected readonly billingHistory = this.billingService.billingHistory;
  protected readonly paymentConfig = this.billingService.paymentConfig;
  protected readonly loading = this.billingService.loading;
  protected readonly error = this.billingService.error;

  protected readonly selectedPlan = signal<Plan | null>(null);
  protected readonly paymentLabel = signal<string>('Simulated Card •••• 4242');
  protected readonly showCheckoutModal = signal<boolean>(false);
  protected readonly showCancelModal = signal<boolean>(false);
  protected readonly submitting = signal<boolean>(false);
  protected readonly actionNotice = signal<string | null>(null);

  protected readonly verifyingPayment = signal<boolean>(false);
  protected readonly pendingCheckoutId = signal<string | null>(null);
  protected readonly verificationError = signal<string | null>(null);

  ngOnInit(): void {
    this.billingService.loadAll().subscribe();

    this.route.queryParams.subscribe(params => {
      if (params['canceled'] === 'true') {
        this.actionNotice.set('Checkout was canceled. Your current plan was not changed.');
        this.router.navigate([], { replaceUrl: true, queryParams: {} });
      } else if (params['checkout_id']) {
        const checkoutId = params['checkout_id'];
        this.pendingCheckoutId.set(checkoutId);
        this.verifyCheckout(checkoutId, 1);
      }
    });
  }

  protected verifyCheckout(checkoutId: string, attempt: number = 1): void {
    this.verifyingPayment.set(true);
    this.verificationError.set(null);

    this.billingService.confirmCheckout(checkoutId).subscribe({
      next: (res) => {
        if (res.status === 'PENDING_CONFIRMATION') {
          if (attempt < 6) {
            const retryDelay = res.retryAfterMs || 1500;
            setTimeout(() => this.verifyCheckout(checkoutId, attempt + 1), retryDelay);
          } else {
            this.verifyingPayment.set(false);
            this.verificationError.set('Payment is still finalizing in Polar. Please retry verification shortly.');
          }
        } else if (res.status === 'ACTIVATED') {
          this.verifyingPayment.set(false);
          this.pendingCheckoutId.set(null);
          const planName = res.subscription?.planName || 'Premium';
          this.actionNotice.set(`Welcome to MindSpark ${planName}! Your subscription is now active.`);
          this.billingService.loadAll().subscribe();
          this.auth.refreshToken();
          this.router.navigate([], { replaceUrl: true, queryParams: {} });
        }
      },
      error: (err) => {
        this.verifyingPayment.set(false);
        const msg = err?.error?.message || err?.message || 'Payment verification failed. Please try again.';
        this.verificationError.set(msg);
      },
    });
  }

  protected retryVerification(): void {
    const id = this.pendingCheckoutId();
    if (id) {
      this.verifyCheckout(id, 1);
    }
  }

  protected isCurrentPlan(plan: Plan): boolean {
    const sub = this.subscription();
    if (!sub) return plan.code === 'FREE';
    return sub.planCode === plan.code && sub.status === 'ACTIVE';
  }

  protected isPaidActive(): boolean {
    const sub = this.subscription();
    return !!sub && sub.status === 'ACTIVE' && sub.planCode !== 'FREE';
  }

  protected getCurrentPlanCode(): string {
    return this.subscription()?.planCode || 'FREE';
  }

  protected getActiveRoomsLimit(): number {
    return this.subscription()?.maxActiveRooms || 3;
  }

  protected getParticipantsLimit(): number {
    return this.subscription()?.maxParticipantsPerRoom || 5;
  }

  protected getAiQuota(): string {
    const limit = this.subscription()?.aiDailyLimit || 10;
    return `${limit} calls / day`;
  }

  protected canTakeAction(plan: Plan): boolean {
    if (this.isCurrentPlan(plan)) return false;
    const currentCode = this.getCurrentPlanCode();
    if (currentCode === 'TEAM') return false; // Already on highest plan
    if (currentCode === 'PREMIUM' && plan.code === 'FREE') return false; // Must cancel instead
    return true;
  }

  protected getButtonText(plan: Plan): string {
    if (this.isCurrentPlan(plan)) return 'Current Plan';
    const currentCode = this.getCurrentPlanCode();
    if (currentCode === 'FREE') return 'Subscribe';
    if (currentCode === 'PREMIUM' && plan.code === 'TEAM') return 'Upgrade to Team';
    return 'Unavailable';
  }

  protected openCheckout(plan: Plan): void {
    if (this.submitting() || this.verifyingPayment()) return;

    if (this.paymentConfig()?.mode === 'POLAR_SANDBOX') {
      this.submitting.set(true);
      this.billingService.startCheckout(plan.code).subscribe({
        next: (res) => {
          if (res.mode === 'POLAR_SANDBOX' && res.checkoutUrl) {
            this.redirectToCheckout(res.checkoutUrl);
          } else {
            this.submitting.set(false);
            this.selectedPlan.set(plan);
            this.paymentLabel.set('Simulated Card •••• 4242');
            this.showCheckoutModal.set(true);
            this.actionNotice.set(null);
          }
        },
        error: () => {
          this.submitting.set(false);
        },
      });
    } else {
      this.selectedPlan.set(plan);
      this.paymentLabel.set('Simulated Card •••• 4242');
      this.showCheckoutModal.set(true);
      this.actionNotice.set(null);
    }
  }

  protected redirectToCheckout(url: string): void {
    window.location.assign(url);
  }

  protected closeCheckout(): void {
    if (this.submitting()) return;
    this.showCheckoutModal.set(false);
    this.selectedPlan.set(null);
  }

  protected submitCheckout(): void {
    const plan = this.selectedPlan();
    if (!plan || this.submitting()) return;

    this.submitting.set(true);
    const currentCode = this.getCurrentPlanCode();
    const label = this.paymentLabel().trim();

    if (currentCode === 'FREE') {
      this.billingService.subscribe(plan.code, label).subscribe({
        next: () => {
          this.actionNotice.set(`Successfully subscribed to ${plan.name} plan!`);
          this.submitting.set(false);
          this.showCheckoutModal.set(false);
          this.selectedPlan.set(null);
        },
        error: () => {
          this.submitting.set(false);
        },
      });
    } else {
      this.billingService.upgrade(plan.code, label).subscribe({
        next: () => {
          this.actionNotice.set(`Successfully upgraded to ${plan.name} plan!`);
          this.submitting.set(false);
          this.showCheckoutModal.set(false);
          this.selectedPlan.set(null);
        },
        error: () => {
          this.submitting.set(false);
        },
      });
    }
  }

  protected openCancelModal(): void {
    this.showCancelModal.set(true);
    this.actionNotice.set(null);
  }

  protected closeCancelModal(): void {
    if (this.submitting()) return;
    this.showCancelModal.set(false);
  }

  protected confirmCancel(): void {
    if (this.submitting()) return;
    this.submitting.set(true);

    this.billingService.cancel().subscribe({
      next: () => {
        this.actionNotice.set('Subscription canceled. Your account has returned to the Free plan. All existing rooms remain active.');
        this.submitting.set(false);
        this.showCancelModal.set(false);
      },
      error: () => {
        this.submitting.set(false);
      },
    });
  }

  protected getProgressWidth(): number {
    const current = this.activeRoomsCount();
    const limit = this.getActiveRoomsLimit();
    if (limit <= 0) return 0;
    return Math.min(100, Math.round((current / limit) * 100));
  }
}
