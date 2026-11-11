import { User } from '../../../core/models/User';

export interface Room {
  id: string;
  name: string;
  invitationCode: string;
  owner?: User | null;
  createdAt?: string;
  members?: User[];
  memberCount: number;
  member?: boolean;
  closed: boolean;
}

export type IdeaSortBy = 'createdAt' | 'voteCount';

export interface IdeaFilters {
  categoryId?: number | null;
  search?: string;
  sortBy?: IdeaSortBy;
}

export interface IdeaPayload {
  title?: string;
  description?: string;
  text?: string;
  x?: number;
  y?: number;
  color?: string;
  categoryId?: number | null;
}