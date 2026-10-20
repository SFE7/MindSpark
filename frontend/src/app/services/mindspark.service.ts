
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, switchMap } from 'rxjs';
import { User } from '../core/models/User';
import { UserService } from '../core/user-service';

// Room response now contains full owner and member objects
export interface Room {
  id: string;
  name: string;
  invitationCode: string;
  owner: User;
  createdAt: string;
  members: User[];
  memberCount: number;
  member: boolean;
}

// Idea response now contains full room and author objects
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
  private readonly userService = inject(UserService);

  /**
   * Synchronizes the currently authenticated Keycloak user
   * with the backend and returns the registered user.
   *
   * The application's HTTP authentication interceptor must
   * attach the Keycloak bearer token to this request.
   */
  syncUser(): Observable<User> {
    return this.userService.syncUser();
  }

  /**
   * Clears the cached user when the application logs out
   * or switches to a different authenticated account.
   */
  clearUserCache(): void {
    this.userService.clearCurrentUser();
  }

  /**
   * Ensures the current Keycloak user is available before using the API.
   */
  private withCurrentUser<T>(request: () => Observable<T>): Observable<T> {
    return this.syncUser().pipe(
      switchMap(() => request())
    );
  }

  health() {
    return this.http.get<{ status: string }>('/api/health');
  }

  // Rooms

  listRooms(): Observable<Room[]> {
    return this.withCurrentUser(() => this.http.get<Room[]>('/api/rooms'));
  }

  getRoom(roomId: string): Observable<Room> {
    return this.withCurrentUser(() => this.http.get<Room>(`/api/rooms/${roomId}`));
  }

  createRoom(name: string): Observable<Room> {
    return this.withCurrentUser(() =>
      this.http.post<Room>(
        '/api/rooms',
        { name }
      )
    );
  }

  joinRoom(invitationCode: string): Observable<Room> {
    return this.withCurrentUser(() =>
      this.http.post<Room>(
        '/api/rooms/join',
        { invitationCode }
      )
    );
  }

  reopenRoom(roomId: string): Observable<Room> {
    return this.withCurrentUser(() =>
      this.http.post<Room>(
        `/api/rooms/${roomId}/reopen`,
        {}
      )
    );
  }

  leaveRoom(roomId: string): Observable<Room> {
    return this.withCurrentUser(() => this.http.delete<Room>(`/api/rooms/${roomId}/leave`));
  }

  // Ideas

  listIdeas(roomId: string): Observable<Idea[]> {
    return this.withCurrentUser(() =>
      this.http.get<Idea[]>(
        `/api/rooms/${roomId}/ideas`
      )
    );
  }

  createIdea(
    roomId: string,
    payload: string | IdeaPayload
  ): Observable<Idea> {
    const body: IdeaPayload =
      typeof payload === 'string'
        ? { text: payload, title: payload }
        : {
            ...payload,
            text: payload.text || payload.title,
            title: payload.title || payload.text
          };

    return this.withCurrentUser(() =>
      this.http.post<Idea>(
        `/api/rooms/${roomId}/ideas`,
        body
      )
    );
  }

  updateIdea(
    ideaId: string,
    payload: string | IdeaPayload
  ): Observable<Idea> {
    const body: IdeaPayload =
      typeof payload === 'string'
        ? { text: payload, title: payload }
        : {
            ...payload,
            text: payload.text || payload.title,
            title: payload.title || payload.text
          };

    return this.withCurrentUser(() =>
      this.http.put<Idea>(
        `/api/ideas/${ideaId}`,
        body
      )
    );
  }

  deleteIdea(ideaId: string): Observable<void> {
    return this.withCurrentUser(() => this.http.delete<void>(`/api/ideas/${ideaId}`));
  }

  // Votes

  vote(ideaId: string): Observable<Idea> {
    return this.withCurrentUser(() => this.http.post<Idea>(`/api/ideas/${ideaId}/vote`, {}));
  }

  removeVote(ideaId: string): Observable<Idea> {
    return this.withCurrentUser(() => this.http.delete<Idea>(`/api/ideas/${ideaId}/vote`));
  }
}
