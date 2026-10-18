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
  text: string;
  createdAt: string;
  updatedAt: string;
  voteCount: number;
  voted: boolean;
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

  createIdea(roomId: string, text: string) {
    return this.http.post<Idea>(`/api/rooms/${roomId}/ideas`, { text });
  }

  updateIdea(ideaId: string, text: string) {
    return this.http.put<Idea>(`/api/ideas/${ideaId}`, { text });
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
