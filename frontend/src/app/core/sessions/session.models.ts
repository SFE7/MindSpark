export type ExportFormat = 'md' | 'json' | 'csv';

export interface ParticipantExportDto {
  keycloakId: string;
  displayName: string;
  role: string;
}

export interface CategoryExportDto {
  id: number;
  name: string;
  color?: string | null;
}

export interface CommentExportDto {
  id: string;
  authorName: string;
  content: string;
  parentCommentId?: string | null;
  createdAt: string;
}

export interface IdeaExportDto {
  id: string;
  title: string;
  text: string;
  categoryId?: number | null;
  categoryName?: string | null;
  authorName: string;
  authorKeycloakId?: string | null;
  voteCount: number;
  createdAt: string;
  comments: CommentExportDto[];
}

export interface BackendRoomExportDto {
  roomId: string;
  roomName: string;
  invitationCode: string;
  closed: boolean;
  createdAt: string;
  ownerKeycloakId: string;
  ownerName: string;
  participants: ParticipantExportDto[];
  categories: CategoryExportDto[];
  ideas: IdeaExportDto[];
}

export interface CreateSnapshotRequest {
  roomId: string;
  title?: string;
  notes?: string;
  aiSummary?: string;
}

export interface SessionSnapshotSummary {
  id: string;
  roomId: string;
  roomName: string;
  invitationCode: string;
  title: string;
  notes?: string | null;
  savedByUserId: string;
  savedByName?: string | null;
  ownerUserId: string;
  ideaCount: number;
  categoryCount: number;
  totalVotes: number;
  participantCount: number;
  createdAt: string;
  hasAiSummary: boolean;
}

export interface SessionSnapshotDetail extends SessionSnapshotSummary {
  aiSummary?: string | null;
  snapshotData: BackendRoomExportDto;
}
