import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { BillingPageComponent } from './billing-page';
import { BillingService } from '../../../../core/billing/billing.service';
import { KeycloakAuth } from '../../../../core/auth/keycloak-auth';
import { Plan, SubscriptionMeResponse } from '../../../../core/billing/billing.models';

describe('BillingPageComponent', () => {
  let component: BillingPageComponent;
  let fixture: ComponentFixture<BillingPageComponent>;
  let billingService: BillingService;

  const mockPlans: Plan[] = [
    {
      id: 'p1',
      code: 'FREE',
      name: 'Free',
      description: 'Starter plan',
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
      description: 'Pro collaboration',
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
  };

  beforeEach(async () => {
    const mockAuth = {
      isLoggedIn: true,
      refreshToken: () => Promise.resolve('token'),
    };

    await TestBed.configureTestingModule({
      imports: [BillingPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: KeycloakAuth, useValue: mockAuth },
      ],
    }).compileComponents();

    billingService = TestBed.inject(BillingService);
    billingService.loadAll = () => of({
      plans: mockPlans,
      subscription: mockSub,
      usage: { activeRoomsCount: 2 },
      history: [],
      paymentConfig: { mode: 'POLAR_SIMULATED', provider: 'SIMULATED', availablePlans: ['PREMIUM', 'TEAM'] },
    });

    fixture = TestBed.createComponent(BillingPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates the component', () => {
    expect(component).toBeTruthy();
  });

  it('renders plan summary and active rooms usage progress bar', () => {
    billingService.plans.set(mockPlans);
    billingService.subscription.set(mockSub);
    billingService.activeRoomsCount.set(2);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('FREE PLAN');
    expect(element.textContent).toContain('2 / 3');
  });

  it('opens and closes simulated checkout modal in POLAR_SIMULATED mode', () => {
    billingService.paymentConfig.set({
      mode: 'POLAR_SIMULATED',
      provider: 'SIMULATED',
      availablePlans: ['PREMIUM', 'TEAM'],
    });

    component['openCheckout'](mockPlans[1]); // Premium
    expect(component['showCheckoutModal']()).toBe(true);
    expect(component['selectedPlan']()?.code).toBe('PREMIUM');

    component['closeCheckout']();
    expect(component['showCheckoutModal']()).toBe(false);
    expect(component['selectedPlan']()).toBeNull();
  });

  it('redirects to Polar checkout URL when in POLAR_SANDBOX mode', () => {
    billingService.paymentConfig.set({
      mode: 'POLAR_SANDBOX',
      provider: 'POLAR',
      availablePlans: ['PREMIUM', 'TEAM'],
    });

    const redirectSpy = vi.spyOn(component as any, 'redirectToCheckout').mockImplementation(() => {});

    billingService.startCheckout = (planCode: string) => of({
      mode: 'POLAR_SANDBOX',
      provider: 'POLAR',
      checkoutId: 'chk_polar_123',
      checkoutUrl: 'https://sandbox.polar.sh/checkout/chk_polar_123',
      planCode,
    });

    component['openCheckout'](mockPlans[1]);

    expect(redirectSpy).toHaveBeenCalledWith('https://sandbox.polar.sh/checkout/chk_polar_123');
    redirectSpy.mockRestore();
  });

  it('handles checkout verification ACTIVATED flow', () => {
    const premiumSub: SubscriptionMeResponse = { ...mockSub, planCode: 'PREMIUM', planName: 'Premium' };
    billingService.confirmCheckout = (checkoutId: string) => of({
      status: 'ACTIVATED',
      alreadyProcessed: false,
      subscription: premiumSub,
      billingRecordId: 'rec_1',
      checkoutId,
      message: 'Activated',
    });

    component['verifyCheckout']('chk_test_123');

    expect(component['verifyingPayment']()).toBe(false);
    expect(component['actionNotice']()).toContain('Welcome to MindSpark Premium!');
  });

  it('opens cancel modal and displays grandfathering note', () => {
    component['openCancelModal']();
    fixture.detectChanges();

    expect(component['showCancelModal']()).toBe(true);
    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Your Rooms Are Safe');
  });
});
