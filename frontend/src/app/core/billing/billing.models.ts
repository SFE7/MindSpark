export interface Plan {
  id: string;
  code: 'FREE' | 'PREMIUM' | 'TEAM' | string;
  name: string;
  description: string;
  priceCents: number;
  currency: string;
  billingInterval: string;
  maxActiveRooms: number;
  maxParticipantsPerRoom: number;
  aiDailyLimit: number;
  aiTier: 'FREE' | 'PREMIUM' | string;
  sortOrder: number;
}

export interface SubscriptionMeResponse {
  planCode: string;
  planName: string;
  status: 'ACTIVE' | 'CANCELED' | 'EXPIRED' | string;
  startedAt: string | null;
  currentPeriodStart: string | null;
  currentPeriodEnd: string | null;
  canceledAt: string | null;
  maxActiveRooms: number;
  maxParticipantsPerRoom: number;
  aiDailyLimit: number;
  aiTier: string;
}

export interface BillingRecord {
  id: string;
  invoiceNumber: string;
  eventType: 'SUBSCRIBE' | 'UPGRADE' | 'CANCEL' | string;
  planCode: string;
  planName: string;
  amountCents: number;
  currency: string;
  status: 'PAID' | 'VOID' | string;
  paymentMethodSummary: string;
  createdAt: string;
}

export interface RoomUsageResponse {
  activeRoomsCount: number;
}

export interface SubscribeRequest {
  planCode: string;
  paymentMethodLabel?: string;
}

export interface UpgradeRequest {
  targetPlanCode: string;
  paymentMethodLabel?: string;
}

export interface PaymentConfigResponse {
  mode: 'POLAR_SANDBOX' | 'SIMULATED' | string;
  provider: 'POLAR' | 'SIMULATED' | string;
  availablePlans: string[];
}

export interface CheckoutResponse {
  mode: 'POLAR_SANDBOX' | 'SIMULATED' | string;
  provider: 'POLAR' | 'SIMULATED' | string;
  checkoutId?: string;
  checkoutUrl?: string;
  planCode: string;
  message?: string;
}

export interface CheckoutConfirmResponse {
  status: 'ACTIVATED' | 'PENDING_CONFIRMATION' | string;
  alreadyProcessed: boolean;
  subscription?: SubscriptionMeResponse;
  billingRecordId?: string;
  checkoutId?: string;
  retryAfterMs?: number;
  message?: string;
}
