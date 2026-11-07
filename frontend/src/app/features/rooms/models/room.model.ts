import { User } from '../../../core/models/User';

export interface Room {
  id: string;
  name: string;
  invitationCode: string;
  owner: User;
  createdAt: string;
  members: User[];
  memberCount: number;
  member: boolean;
  closed?: boolean;
}

export interface IdeaPayload {
  title?: string;
  description?: string;
  text?: string;
  x?: number;
  y?: number;
  color?: string;
}

