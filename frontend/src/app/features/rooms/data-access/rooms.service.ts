import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, switchMap } from 'rxjs';
import { UserService } from '../../../core/user-service';
import { Room, IdeaPayload } from '../models/room.model';
import { User } from '../../../core/models/User';

@Injectable({ providedIn: 'root' })
export class RoomsService {
  private readonly http = inject(HttpClient);
  private readonly userService = inject(UserService);

  // Delegation methods — kept for backward-compat with any existing callers
  syncUser(): Observable<User> {
    return this.userService.syncUser();
  }

  clearUserCache(): void {
    this.userService.clearCurrentUser();
  }

  private withCurrentUser<T>(request: () => Observable<T>): Observable<T> {
    return this.syncUser().pipe(
      switchMap(() => request())
    );
  }

  health() {
    return this.http.get<{ status: string }>('/api/health');
  }

  listRooms(): Observable<Room[]> {
    return this.withCurrentUser(() => this.http.get<Room[]>('/api/rooms'));
  }

  getRoom(roomId: string): Observable<Room> {
    return this.withCurrentUser(() => this.http.get<Room>(`/api/rooms/${roomId}`));
  }

  createRoom(name: string): Observable<Room> {
    return this.withCurrentUser(() =>
      this.http.post<Room>('/api/rooms', { name })
    );
  }

  joinRoom(invitationCode: string): Observable<Room> {
    return this.withCurrentUser(() =>
      this.http.post<Room>('/api/rooms/join', { invitationCode })
    );
  }

  reopenRoom(roomId: string): Observable<Room> {
    return this.withCurrentUser(() =>
      this.http.post<Room>(`/api/rooms/${roomId}/reopen`, {})
    );
  }

  leaveRoom(roomId: string): Observable<Room> {
    return this.withCurrentUser(() => this.http.delete<Room>(`/api/rooms/${roomId}/leave`));
  }
}

