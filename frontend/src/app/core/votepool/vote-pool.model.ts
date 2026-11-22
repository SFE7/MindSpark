export interface VotePoolTally {
  ideaId: string;
  ideaTitle: string;
  poolVotes: number;
}

export interface VotePool {
  id: string;
  roomId: string;
  title: string;
  votesPerParticipant: number;
  allowMultipleVotesPerIdea: boolean;
  status: 'OPEN' | 'CLOSED';
  createdById?: number;
  createdBySub?: string;
  createdByName?: string;
  createdAt: string;
  closedAt?: string;
  myVotesUsed: number;
  myVotesRemaining: number;
  myVotesByIdeaId: Record<string, number>;
  tallies: VotePoolTally[];
}

export interface CreateVotePoolRequest {
  title: string;
  votesPerParticipant?: number;
  allowMultipleVotesPerIdea?: boolean;
}
