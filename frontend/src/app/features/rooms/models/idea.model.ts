import { User } from '../../../core/models/User';
import { Room } from './room.model';

export interface Idea {
  id: string;
  room: Room;
  author: User;
  text?: string;
  title?: string;
  description?: string;
  x?: number;
  y?: number;
  color?: string;
  createdAt: string;
  updatedAt: string;
  voteCount: number;
  voted: boolean;
}

