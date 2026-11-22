import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { BillingService } from './billing.service';
import { KeycloakAuth } from '../auth/keycloak-auth';
import { AiService } from '../ai/ai.service';
import { Plan, SubscriptionMeResponse, PaymentConfigResponse, CheckoutResponse, CheckoutConfirmResponse } from './billing.models';

describe('BillingService', () => {
  let service: BillingService;
  let httpTesting: HttpTestingController;
  let refreshTokenCalled: boolean;
  let aiLoadCalled: boolean;

  const mockPaymentConfig: PaymentConfigResponse = {
    mode: 'POLAR_SANDBOX',
    provider: 'POLAR',
    availablePlans: ['PREMIUM', 'TEAM'],
  };

  const mockPlans: Plan[] = [
    {
      id: 'p1',
      code: 'FREE',
      name: 'Free',
      description: 'Basic',
      priceCents: 0,
      currency: 'USD',
      billingInterval: 'MONTHLY',
      maxActiveRooms: 3,
      maxParticipantsPerRoom: 5,
      aiDailyLimit: 10,
      aiTier: 'FREE',
      sortOrder: 1,
    },
    {
      id: 'p2',
      code: 'PREMIUM',
      name: 'Premium',
      description: 'Expanded',
      priceCents: 900,
      currency: 'USD',
      billingInterval: 'MONTHLY',
      maxActiveRooms: 20,
      maxParticipantsPerRoom: 25,
      aiDailyLimit: 20,
      aiTier: 'PREMIUM',
      sortOrder: 2,
    },
  ];

  const mockSub: SubscriptionMeResponse = {
    planCode: 'PREMIUM',
    planName: 'Premium',
    status: 'ACTIVE',
    startedAt: '2026-11-20T12:00:00Z',
    currentPeriodStart: '2026-11-20T12:00:00Z',
    currentPeriodEnd: '2026-12-20T12:00:00Z',
    canceledAt: null,
    maxActiveRooms: 20,
    maxParticipantsPerRoom: 25,
    aiDailyLimit: 20,
    aiTier: 'PREMIUM',
  };

  beforeEach(() => {
    TestBed.resetTestingModule();
    refreshTokenCalled = false;
    aiLoadCalled = false;

    const mockAuth = {
      refreshToken: () => {
        refreshTokenCalled = true;
        return Promise.resolve('fake-token');
      },
    };

    const mockAi = {
      loadUserAndUsage: () => {
        aiLoadCalled = true;
        return of({
          user: { userId: 'u1', tier: 'PREMIUM' },
          usage: { tier: 'PREMIUM', dailyLimit: 20, usedToday: 2, remainingToday: 18, resetsAtUtc: '2026-11-21T00:00:00Z' },
        });
      },
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        BillingService,
        { provide: KeycloakAuth, useValue: mockAuth },
        { provide: AiService, useValue: mockAi },
      ],
    });

    service = TestBed.inject(BillingService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    try {
      httpTesting.verify();
    } finally {
      TestBed.resetTestingModule();
    }
  });

  it('initializes with default signal states', () => {
    expect(service.plans()).toEqual([]);
    expect(service.subscription()).toBeNull();
    expect(service.activeRoomsCount()).toBe(0);
    expect(service.billingHistory()).toEqual([]);
    expect(service.loading()).toBe(false);
  });

  it('loadAll fetches /api/plans, /api/subscriptions/me, /api/rooms/usage, /api/billing/history, /api/subscriptions/payment-config and updates signals', () => {
    service.loadAll().subscribe(result => {
      expect(result.plans.length).toBe(2);
      expect(result.subscription.planCode).toBe('PREMIUM');
      expect(result.usage.activeRoomsCount).toBe(2);
      expect(result.history.length).toBe(1);
      expect(result.paymentConfig.mode).toBe('POLAR_SANDBOX');

      expect(service.plans().length).toBe(2);
      expect(service.subscription()?.planCode).toBe('PREMIUM');
      expect(service.activeRoomsCount()).toBe(2);
      expect(service.billingHistory().length).toBe(1);
      expect(service.paymentConfig()?.mode).toBe('POLAR_SANDBOX');
    });

    const plansReq = httpTesting.expectOne('/api/plans');
    expect(plansReq.request.method).toBe('GET');
    plansReq.flush(mockPlans);

    const subReq = httpTesting.expectOne('/api/subscriptions/me');
    expect(subReq.request.method).toBe('GET');
    subReq.flush(mockSub);

    const usageReq = httpTesting.expectOne('/api/rooms/usage');
    expect(usageReq.request.method).toBe('GET');
    usageReq.flush({ activeRoomsCount: 2 });

    const histReq = httpTesting.expectOne('/api/billing/history');
    expect(histReq.request.method).toBe('GET');
    histReq.flush([
      {
        id: 'h1',
        invoiceNumber: 'INV-2026-000001',
        eventType: 'SUBSCRIBE',
        planCode: 'PREMIUM',
        planName: 'Premium',
        amountCents: 900,
        currency: 'USD',
        status: 'PAID',
        paymentMethodSummary: 'Test Card',
        createdAt: '2026-11-20T12:00:00Z',
      },
    ]);

    const configReq = httpTesting.expectOne('/api/subscriptions/payment-config');
    expect(configReq.request.method).toBe('GET');
    configReq.flush(mockPaymentConfig);
  });

  it('subscribe triggers POST /api/subscriptions/subscribe, calls auth.refreshToken() and aiService.loadUserAndUsage()', async () => {
    let receivedSub: SubscriptionMeResponse | null = null;
    service.subscribe('PREMIUM', 'Card 4242').subscribe(res => {
      receivedSub = res;
    });

    const subReq = httpTesting.expectOne('/api/subscriptions/subscribe');
    expect(subReq.request.method).toBe('POST');
    expect(subReq.request.body).toEqual({ planCode: 'PREMIUM', paymentMethodLabel: 'Card 4242' });
    subReq.flush(mockSub);

    await Promise.resolve();
    await new Promise(r => setTimeout(r, 10));

    expect(receivedSub).toBeTruthy();
    expect(refreshTokenCalled).toBe(true);
    expect(aiLoadCalled).toBe(true);

    // loadAll sub-requests triggered after action
    httpTesting.expectOne('/api/plans').flush(mockPlans);
    httpTesting.expectOne('/api/subscriptions/me').flush(mockSub);
    httpTesting.expectOne('/api/rooms/usage').flush({ activeRoomsCount: 1 });
    httpTesting.expectOne('/api/billing/history').flush([]);
    httpTesting.expectOne('/api/subscriptions/payment-config').flush(mockPaymentConfig);
  });

  it('upgrade triggers POST /api/subscriptions/upgrade, calls auth.refreshToken() and aiService.loadUserAndUsage()', async () => {
    const teamSub: SubscriptionMeResponse = { ...mockSub, planCode: 'TEAM', planName: 'Team', maxActiveRooms: 100 };
    let receivedSub: SubscriptionMeResponse | null = null;
    service.upgrade('TEAM').subscribe(res => {
      receivedSub = res;
    });

    const upReq = httpTesting.expectOne('/api/subscriptions/upgrade');
    expect(upReq.request.method).toBe('POST');
    upReq.flush(teamSub);

    await Promise.resolve();
    await new Promise(r => setTimeout(r, 10));

    expect(receivedSub).toBeTruthy();
    expect(refreshTokenCalled).toBe(true);
    expect(aiLoadCalled).toBe(true);

    httpTesting.expectOne('/api/plans').flush(mockPlans);
    httpTesting.expectOne('/api/subscriptions/me').flush(teamSub);
    httpTesting.expectOne('/api/rooms/usage').flush({ activeRoomsCount: 1 });
    httpTesting.expectOne('/api/billing/history').flush([]);
    httpTesting.expectOne('/api/subscriptions/payment-config').flush(mockPaymentConfig);
  });

  it('cancel triggers POST /api/subscriptions/cancel, calls auth.refreshToken() and aiService.loadUserAndUsage()', async () => {
    const freeSub: SubscriptionMeResponse = { ...mockSub, planCode: 'FREE', planName: 'Free', status: 'ACTIVE', maxActiveRooms: 3 };
    let receivedSub: SubscriptionMeResponse | null = null;
    service.cancel().subscribe(res => {
      receivedSub = res;
    });

    const cancelReq = httpTesting.expectOne('/api/subscriptions/cancel');
    expect(cancelReq.request.method).toBe('POST');
    cancelReq.flush(freeSub);

    await Promise.resolve();
    await new Promise(r => setTimeout(r, 10));

    expect(receivedSub).toBeTruthy();
    expect(refreshTokenCalled).toBe(true);
    expect(aiLoadCalled).toBe(true);

    httpTesting.expectOne('/api/plans').flush(mockPlans);
    httpTesting.expectOne('/api/subscriptions/me').flush(freeSub);
    httpTesting.expectOne('/api/rooms/usage').flush({ activeRoomsCount: 1 });
    httpTesting.expectOne('/api/billing/history').flush([]);
    httpTesting.expectOne('/api/subscriptions/payment-config').flush(mockPaymentConfig);
  });

  it('getPaymentConfig fetches /api/subscriptions/payment-config and updates paymentConfig signal', () => {
    service.getPaymentConfig().subscribe(res => {
      expect(res.mode).toBe('POLAR_SANDBOX');
      expect(service.paymentConfig()?.mode).toBe('POLAR_SANDBOX');
    });

    const req = httpTesting.expectOne('/api/subscriptions/payment-config');
    expect(req.request.method).toBe('GET');
    req.flush(mockPaymentConfig);
  });

  it('startCheckout sends POST /api/subscriptions/checkout with planCode and returnOrigin', () => {
    const mockCheckoutResp: CheckoutResponse = {
      mode: 'POLAR_SANDBOX',
      provider: 'POLAR',
      checkoutId: 'chk_123',
      checkoutUrl: 'https://sandbox.polar.sh/checkout/chk_123',
      planCode: 'PREMIUM',
    };

    service.startCheckout('PREMIUM').subscribe(res => {
      expect(res.checkoutId).toBe('chk_123');
      expect(res.checkoutUrl).toBe('https://sandbox.polar.sh/checkout/chk_123');
    });

    const req = httpTesting.expectOne('/api/subscriptions/checkout');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ planCode: 'PREMIUM', returnOrigin: window.location.origin });
    req.flush(mockCheckoutResp);
  });

  it('confirmCheckout sends POST /api/subscriptions/checkout/confirm and triggers auth & ai refresh on ACTIVATED', async () => {
    const mockConfirmResp: CheckoutConfirmResponse = {
      status: 'ACTIVATED',
      alreadyProcessed: false,
      subscription: mockSub,
      billingRecordId: 'rec_123',
      checkoutId: 'chk_123',
      message: 'Subscription activated successfully',
    };

    service.confirmCheckout('chk_123').subscribe(res => {
      expect(res.status).toBe('ACTIVATED');
    });

    const req = httpTesting.expectOne('/api/subscriptions/checkout/confirm');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ checkoutId: 'chk_123' });
    req.flush(mockConfirmResp);

    await Promise.resolve();
    await new Promise(r => setTimeout(r, 10));

    expect(refreshTokenCalled).toBe(true);
    expect(aiLoadCalled).toBe(true);

    httpTesting.expectOne('/api/plans').flush(mockPlans);
    httpTesting.expectOne('/api/subscriptions/me').flush(mockSub);
    httpTesting.expectOne('/api/rooms/usage').flush({ activeRoomsCount: 1 });
    httpTesting.expectOne('/api/billing/history').flush([]);
    httpTesting.expectOne('/api/subscriptions/payment-config').flush(mockPaymentConfig);
  });
});
