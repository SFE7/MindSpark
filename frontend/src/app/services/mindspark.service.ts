import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface Room {
  id: string;
  name: string;
  invitationCode: string;
  ownerGuestId: string;
  createdAt: string;
  memberCount: number;
  member: boolean;
}

export interface Idea {
  id: string;
  roomId: string;
  authorGuestId: string;
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

export interface IdeaPayload {
  title?: string;
  description?: string;
  text?: string;
  x?: number;
  y?: number;
  color?: string;
}

@Injectable({ providedIn: 'root' })
export class MindsparkService {
  private readonly http = inject(HttpClient);

  health() {
    return this.http.get<{ status: string }>('/api/health');
  }

  listRooms() {
    return this.http.get<Room[]>('/api/rooms');
  }

  getRoom(roomId: string) {
    return this.http.get<Room>(`/api/rooms/${roomId}`);
  }

  createRoom(name: string) {
    return this.http.post<Room>('/api/rooms', { name });
  }

  joinRoom(invitationCode: string) {
    return this.http.post<Room>('/api/rooms/join', { invitationCode });
  }

  reopenRoom(roomId: string) {
    return this.http.post<Room>(`/api/rooms/${roomId}/reopen`, {});
  }

  leaveRoom(roomId: string) {
    return this.http.delete<Room>(`/api/rooms/${roomId}/leave`);
  }

  listIdeas(roomId: string) {
    return this.http.get<Idea[]>(`/api/rooms/${roomId}/ideas`);
  }

  createIdea(roomId: string, payload: string | IdeaPayload) {
    const body = typeof payload === 'string' ? { text: payload, title: payload } : {
      ...payload,
      text: payload.text || payload.title,
      title: payload.title || payload.text,
    };
    return this.http.post<Idea>(`/api/rooms/${roomId}/ideas`, body);
  }

  updateIdea(ideaId: string, payload: string | IdeaPayload) {
    const body = typeof payload === 'string' ? { text: payload, title: payload } : {
      ...payload,
      text: payload.text || payload.title,
      title: payload.title || payload.text,
    };
    return this.http.put<Idea>(`/api/ideas/${ideaId}`, body);
  }

  deleteIdea(ideaId: string) {
    return this.http.delete<void>(`/api/ideas/${ideaId}`);
  }

  vote(ideaId: string) {
    return this.http.post<Idea>(`/api/ideas/${ideaId}/vote`, {});
  }

  removeVote(ideaId: string) {
    return this.http.delete<Idea>(`/api/ideas/${ideaId}/vote`);
  }
}
