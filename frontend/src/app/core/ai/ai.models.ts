export type AiTier = 'FREE' | 'PREMIUM';

export interface AiMeta {
  model: string;
  tier: AiTier | string;
}

export interface AiResponse<T> {
  result: T;
  meta: AiMeta;
}

export interface AiErrorBody {
  error: string;
  message: string;
  retryAfterSeconds?: number;
}

export type AiErrorCode =
  | 'VALIDATION_ERROR'
  | 'DAILY_LIMIT_REACHED'
  | 'PROVIDER_BUSY'
  | 'AI_UNAVAILABLE'
  | 'BAD_MODEL_OUTPUT'
  | 'UNKNOWN_ERROR';

export interface NormalizedAiError {
  code: AiErrorCode;
  message: string;
  retryAfterSeconds?: number;
  canRetry: boolean;
}

export interface AiUserResponse {
  userId: string;
  tier: AiTier;
}

export interface AiUsageResponse {
  tier: AiTier;
  dailyLimit: number;
  usedToday: number;
  remainingToday: number;
  resetsAtUtc: string;
}

export interface AiRoomIdea {
  id: string;
  title?: string;
  description?: string;
  text?: string;
  voteCount?: number;
  category?: { id?: number; title: string } | null;
}

/* Feature 1: Generate Ideas */
export interface GenerateIdeasRequest {
  topic: string;
  context?: string;
  count?: number;
}

export interface GeneratedIdeaItem {
  title: string;
  description: string;
}

export interface GenerateIdeasResult {
  ideas: GeneratedIdeaItem[];
}

/* Feature 2: Improve Idea */
export type ImproveIdeaMode = 'improve' | 'expand';

export interface ImproveIdeaPayload {
  id?: string;
  text: string;
}

export interface ImproveIdeaRequest {
  idea: ImproveIdeaPayload;
  mode: ImproveIdeaMode;
}

export interface ImproveIdeaResult {
  title: string;
  text: string;
  changes: string[];
}

/* Feature 3: Group Ideas */
export interface GroupIdeaItem {
  id: string;
  text: string;
}

export interface GroupIdeasRequest {
  ideas: GroupIdeaItem[];
}

export interface GroupItem {
  name: string;
  ideaIds: string[];
  rationale: string;
}

export interface GroupIdeasResult {
  groups: GroupItem[];
  ungroupedIds: string[];
}

/* Feature 4: Action Plan */
export interface ActionPlanIdeaItem {
  id: string;
  text: string;
}

export interface ActionPlanRequest {
  goal?: string;
  ideas: ActionPlanIdeaItem[];
}

export interface PlanStep {
  order: number;
  title: string;
  description: string;
  priority: string;
  relatedIdeaIds: string[];
}

export interface ActionPlanResult {
  title: string;
  steps: PlanStep[];
  quickWins: string[];
}

/* Feature 5: Find Risks */
export interface FindRisksIdeaPayload {
  id?: string;
  text: string;
}

export interface FindRisksRequest {
  idea: FindRisksIdeaPayload;
  context?: string;
}

export interface RiskItem {
  title: string;
  description: string;
  severity: string;
  mitigation: string;
}

export interface FindRisksResult {
  risks: RiskItem[];
}

/* Feature 6: Compare Ideas */
export interface CompareIdeaItem {
  id: string;
  text: string;
}

export interface CompareIdeasRequest {
  ideas: CompareIdeaItem[];
}

export interface ComparisonItem {
  ideaId: string;
  scores: Record<string, number>;
  strengths: string[];
  weaknesses: string[];
}

export interface CompareRecommendation {
  ideaId: string;
  reason: string;
}

export interface CompareIdeasResult {
  criteria: string[];
  comparison: ComparisonItem[];
  recommendation: CompareRecommendation;
}

/* Feature 7: Summarize Session */
export interface SessionIdeaItem {
  id: string;
  text: string;
  votes?: number;
  category?: string;
}

export interface SummarizeSessionRequest {
  sessionTitle?: string;
  ideas: SessionIdeaItem[];
}

export interface TopIdeaItem {
  id: string;
  reason: string;
}

export interface SummarizeSessionResult {
  summary: string;
  themes: string[];
  topIdeas: TopIdeaItem[];
  openQuestions: string[];
}

/* Validation Caps matching Backend AiCapsProperties and DTO constraints */
export const AI_CAPS = {
  maxTopicChars: 500,
  maxIdeaChars: 300,
  maxPayloadChars: 6000,
  generateMinCount: 1,
  generateMaxCount: 10,
  generateDefaultCount: 5,
  groupMinIdeas: 2,
  groupMaxIdeas: 30,
  actionPlanMinIdeas: 1,
  actionPlanMaxIdeas: 15,
  compareMinIdeas: 2,
  compareMaxIdeas: 4,
  summarizeMinIdeas: 1,
  summarizeMaxIdeas: 30,
} as const;
