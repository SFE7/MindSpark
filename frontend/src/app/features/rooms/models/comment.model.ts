import { User } from '../../../core/models/User';

export interface CommentItem {
  id: string;
  ideaId: string;
  author: User;
  content: string;
  text?: string;
  parentId?: string | null;
  createdAt: string;
  updatedAt: string;
  replies?: CommentItem[];
}
