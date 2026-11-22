export interface PlatformStats {
  totalUsers: number;
  totalRooms: number;
  activeRooms: number;
  closedRooms: number;
  totalIdeas: number;
  totalVotes: number;
  recentRooms: Array<{
    id: string;
    name: string;
    invitationCode: string;
    ownerUsername: string;
    createdAt: string;
    closed: boolean;
    memberCount: number;
  }>;
}

export interface BillingStats {
  totalSubscriptionsCount: number;
  activePaidSubscriptionsCount: number;
  subscribersByPlan: Array<{
    planCode: string;
    planName: string;
    activeCount: number;
  }>;
  totalRevenueCents: number;
  estimatedMrrCents: number;
  recentBillingEventsCount30d: number;
  recentBillingRecords: Array<{
    id: string;
    invoiceNumber: string;
    eventType: string;
    planCode: string;
    planName: string;
    amountCents: number;
    currency: string;
    status: string;
    paymentMethodSummary: string;
    createdAt: string;
  }>;
}

export interface AiStats {
  callsTodayUtc: number;
  totalCallsAllTime: number;
  activeAiUsersToday: number;
  tierBreakdownToday: Record<string, number>;
}

export interface AdminUserDirectory {
  id: number;
  keycloakId: string;
  email: string;
  fullName: string;
  createdAt: string;
}

export interface AdminPlan {
  id: string;
  code: string;
  name: string;
  description: string;
  priceCents: number;
  currency: string;
  billingInterval: string;
  maxActiveRooms: number;
  maxParticipantsPerRoom: number;
  aiDailyLimit: number;
  aiTier: string;
  active: boolean;
  sortOrder: number;
  activeSubscribersCount: number;
}

export interface CreatePlanRequest {
  code: string;
  name: string;
  description?: string;
  priceCents: number;
  currency: string;
  billingInterval: string;
  maxActiveRooms: number;
  maxParticipantsPerRoom: number;
  aiDailyLimit: number;
  aiTier: string;
  sortOrder: number;
}

export interface UpdatePlanRequest {
  name: string;
  description?: string;
  priceCents: number;
  currency: string;
  billingInterval: string;
  maxActiveRooms: number;
  maxParticipantsPerRoom: number;
  aiDailyLimit: number;
  aiTier: string;
  active: boolean;
  sortOrder: number;
}

export interface AdminSubscription {
  id: string;
  userId: string;
  planCode: string;
  planName: string;
  status: string;
  startedAt: string;
  currentPeriodStart: string;
  currentPeriodEnd: string;
  canceledAt: string | null;
  createdAt: string;
}

export interface AdminSubscriptionsPage {
  items: AdminSubscription[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AdminAssignRequest {
  userId: string;
  planCode: string;
  reason?: string;
}

export interface AdminCancelResponse {
  subscription: AdminSubscription;
  roleSyncStatus: string;
}

export interface CreateAdminRequest {
  email: string;
  firstName: string;
  lastName: string;
  temporaryPassword: string;
}

export interface AdminUserResponse {
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
  temporaryPasswordRequired: boolean;
}

export interface AdminUserSummary {
  userId: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
  createdTimestamp: number | null;
}

export interface AdminListResponse {
  configured: boolean;
  admins: AdminUserSummary[];
}
