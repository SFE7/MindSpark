import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AdminService } from './admin.service';
import { AiStats, BillingStats, PlatformStats } from './admin.models';

describe('AdminService', () => {
  let service: AdminService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AdminService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(AdminService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('loadOverviewStats resilience', () => {
    it('populates platform & ai stats even if billing service fails with 500 (partial failure isolation)', () => {
      const mockPlatform: PlatformStats = {
        totalUsers: 45,
        totalRooms: 12,
        activeRooms: 10,
        closedRooms: 2,
        totalIdeas: 88,
        totalVotes: 140,
        recentRooms: [],
      };

      const mockAi: AiStats = {
        callsTodayUtc: 24,
        totalCallsAllTime: 310,
        activeAiUsersToday: 5,
        tierBreakdownToday: { TOTAL: 24 },
      };

      service.loadOverviewStats().subscribe();

      const platformReq = httpTesting.expectOne('/api/admin/stats/platform');
      platformReq.flush(mockPlatform);

      const billingReq = httpTesting.expectOne('/api/admin/stats/billing');
      billingReq.flush({ message: 'Billing database connection error' }, { status: 500, statusText: 'Server Error' });

      const aiReq = httpTesting.expectOne('/api/admin/stats/ai');
      aiReq.flush(mockAi);

      // Verify healthy metrics are populated despite billing failure
      expect(service.platformStats()).toEqual(mockPlatform);
      expect(service.aiStats()).toEqual(mockAi);
      expect(service.billingStats()).toBeNull();
      expect(service.billingError()).toContain('Billing database connection error');
      expect(service.platformError()).toBeNull();
      expect(service.aiError()).toBeNull();
      expect(service.overviewLoading()).toBe(false);
    });
  });

  describe('Plans management', () => {
    it('loads plans and sets signal', () => {
      const mockPlans = [
        {
          id: 'p1',
          code: 'FREE',
          name: 'Free',
          description: '',
          priceCents: 0,
          currency: 'USD',
          billingInterval: 'MONTHLY',
          maxActiveRooms: 3,
          maxParticipantsPerRoom: 5,
          aiDailyLimit: 10,
          aiTier: 'FREE',
          active: true,
          sortOrder: 1,
          activeSubscribersCount: 20,
        },
      ];

      service.loadPlans().subscribe(data => {
        expect(data).toEqual(mockPlans);
      });

      const req = httpTesting.expectOne('/api/admin/plans');
      req.flush(mockPlans);
      expect(service.plans()).toEqual(mockPlans);
    });

    it('creates a new plan via POST', () => {
      const newPlan = {
        code: 'SCALE',
        name: 'Scale',
        priceCents: 4900,
        currency: 'USD',
        billingInterval: 'MONTHLY',
        maxActiveRooms: 50,
        maxParticipantsPerRoom: 50,
        aiDailyLimit: 100,
        aiTier: 'PREMIUM',
        sortOrder: 4,
      };

      service.createPlan(newPlan).subscribe();

      const createReq = httpTesting.expectOne('/api/admin/plans');
      expect(createReq.request.method).toBe('POST');
      expect(createReq.request.body).toEqual(newPlan);
      createReq.flush({ ...newPlan, id: 'new-id', active: true, activeSubscribersCount: 0 });

      // Automatically reloads plans
      const reloadReq = httpTesting.expectOne('/api/admin/plans');
      reloadReq.flush([]);
    });
  });

  describe('Subscriptions management', () => {
    it('queries subscriptions with filter query parameters', () => {
      service.loadSubscriptions({ status: 'ACTIVE', planCode: 'PREMIUM', page: 1, size: 10 }).subscribe();

      const req = httpTesting.expectOne(r =>
        r.url === '/api/admin/subscriptions' &&
        r.params.get('status') === 'ACTIVE' &&
        r.params.get('planCode') === 'PREMIUM' &&
        r.params.get('page') === '1' &&
        r.params.get('size') === '10'
      );
      expect(req.request.method).toBe('GET');
      req.flush({ items: [], page: 1, size: 10, totalElements: 0, totalPages: 0 });
    });

    it('assigns subscription via POST /api/admin/subscriptions/assign', () => {
      const assignReq = { userId: 'u1', planCode: 'PREMIUM', reason: 'Admin test' };
      service.assignSubscription(assignReq).subscribe();

      const req = httpTesting.expectOne('/api/admin/subscriptions/assign');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(assignReq);
      req.flush({ id: 's1', userId: 'u1', planCode: 'PREMIUM', status: 'ACTIVE' });
    });

    it('cancels subscription via POST /api/admin/subscriptions/:userId/cancel', () => {
      service.cancelSubscription('user-123').subscribe();

      const req = httpTesting.expectOne('/api/admin/subscriptions/user-123/cancel');
      expect(req.request.method).toBe('POST');
      req.flush({ subscription: { id: 's1', status: 'CANCELED' }, roleSyncStatus: 'SUCCESS' });
    });
  });

  describe('Admin provisioning', () => {
    it('provisions admin via POST /api/admin/admins', () => {
      const createReq = {
        email: 'newadmin@mindspark.com',
        firstName: 'New',
        lastName: 'Admin',
        temporaryPassword: 'tempPassword123!',
      };

      service.createAdmin(createReq).subscribe();

      const req = httpTesting.expectOne('/api/admin/admins');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(createReq);
      req.flush({ userId: 'kc-1', email: createReq.email, role: 'admin', temporaryPasswordRequired: true });
    });
  });
});
