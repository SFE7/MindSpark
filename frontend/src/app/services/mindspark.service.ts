import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { User } from '../core/models/User';
import { RoomsService } from '../features/rooms/data-access/rooms.service';
import { IdeasService } from '../features/rooms/data-access/ideas.service';
import { Room, IdeaPayload } from '../features/rooms/models/room.model';
import { Idea } from '../features/rooms/models/idea.model';

// Re-export so existing import paths keep working during transition
export type { Room, IdeaPayload } from '../features/rooms/models/room.model';
export type { Idea } from '../features/rooms/models/idea.model';

@Injectable({ providedIn: 'root' })
export class MindsparkService {
  private readonly roomsSvc = inject(RoomsService);
  private readonly ideasSvc = inject(IdeasService);

  syncUser(): Observable<User> {
    return this.roomsSvc.syncUser();
  }

  clearUserCache(): void {
    this.roomsSvc.clearUserCache();
  }

  health() {
    return this.roomsSvc.health();
  }

  // ── Rooms ────────────────────────────────────────────────────────────────

  listRooms(): Observable<Room[]> {
    return this.roomsSvc.listRooms();
  }

  getRoom(roomId: string): Observable<Room> {
    return this.roomsSvc.getRoom(roomId);
  }

  createRoom(name: string): Observable<Room> {
    return this.roomsSvc.createRoom(name);
  }

  joinRoom(invitationCode: string): Observable<Room> {
    return this.roomsSvc.joinRoom(invitationCode);
  }

  reopenRoom(roomId: string): Observable<Room> {
    return this.roomsSvc.reopenRoom(roomId);
  }

  leaveRoom(roomId: string): Observable<Room> {
    return this.roomsSvc.leaveRoom(roomId);
  }

  // ── Ideas ────────────────────────────────────────────────────────────────

  listIdeas(roomId: string): Observable<Idea[]> {
    return this.ideasSvc.listIdeas(roomId);
  }

  createIdea(roomId: string, payload: string | IdeaPayload): Observable<Idea> {
    return this.ideasSvc.createIdea(roomId, payload);
  }

  updateIdea(ideaId: string, payload: string | IdeaPayload): Observable<Idea> {
    return this.ideasSvc.updateIdea(ideaId, payload);
  }

  deleteIdea(ideaId: string): Observable<void> {
    return this.ideasSvc.deleteIdea(ideaId);
  }

  // ── Votes ────────────────────────────────────────────────────────────────

  vote(ideaId: string): Observable<Idea> {
    return this.ideasSvc.vote(ideaId);
  }

  removeVote(ideaId: string): Observable<Idea> {
    return this.ideasSvc.removeVote(ideaId);
  }
}
