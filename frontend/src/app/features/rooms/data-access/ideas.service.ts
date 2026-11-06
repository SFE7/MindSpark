import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, switchMap } from 'rxjs';
import { UserService } from '../../../core/user-service';
import { Idea } from '../models/idea.model';
import { IdeaPayload } from '../models/room.model';

@Injectable({ providedIn: 'root' })
export class IdeasService {
  private readonly http = inject(HttpClient);
  private readonly userService = inject(UserService);

  private withCurrentUser<T>(request: () => Observable<T>): Observable<T> {
    return this.userService.syncUser().pipe(
      switchMap(() => request())
    );
  }

  listIdeas(roomId: string): Observable<Idea[]> {
    return this.withCurrentUser(() =>
      this.http.get<Idea[]>(`/api/rooms/${roomId}/ideas`)
    );
  }

  createIdea(roomId: string, payload: string | IdeaPayload): Observable<Idea> {
    const body: IdeaPayload =
      typeof payload === 'string'
        ? { text: payload, title: payload }
        : {
            ...payload,
            text: payload.text || payload.title,
            title: payload.title || payload.text,
          };

    return this.withCurrentUser(() =>
      this.http.post<Idea>(`/api/rooms/${roomId}/ideas`, body)
    );
  }

  updateIdea(ideaId: string, payload: string | IdeaPayload): Observable<Idea> {
    const body: IdeaPayload =
      typeof payload === 'string'
        ? { text: payload, title: payload }
        : {
            ...payload,
            text: payload.text || payload.title,
            title: payload.title || payload.text,
          };

    return this.withCurrentUser(() =>
      this.http.put<Idea>(`/api/ideas/${ideaId}`, body)
    );
  }

  deleteIdea(ideaId: string): Observable<void> {
    return this.withCurrentUser(() => this.http.delete<void>(`/api/ideas/${ideaId}`));
  }

  vote(ideaId: string): Observable<Idea> {
    return this.withCurrentUser(() => this.http.post<Idea>(`/api/ideas/${ideaId}/vote`, {}));
  }

  removeVote(ideaId: string): Observable<Idea> {
    return this.withCurrentUser(() => this.http.delete<Idea>(`/api/ideas/${ideaId}/vote`));
  }
}

