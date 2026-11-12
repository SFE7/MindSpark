import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, switchMap } from 'rxjs';
import { UserService } from '../../../core/user-service';
import { CommentItem } from '../models/comment.model';

@Injectable({ providedIn: 'root' })
export class CommentsService {
  private readonly http = inject(HttpClient);
  private readonly userService = inject(UserService);

  private withCurrentUser<T>(request: () => Observable<T>): Observable<T> {
    return this.userService.syncUser().pipe(
      switchMap(() => request())
    );
  }

  getComments(ideaId: string): Observable<CommentItem[]> {
    return this.withCurrentUser(() =>
      this.http.get<CommentItem[]>(`/api/ideas/${ideaId}/comments`)
    );
  }

  addComment(ideaId: string, content: string): Observable<CommentItem> {
    return this.withCurrentUser(() =>
      this.http.post<CommentItem>(`/api/ideas/${ideaId}/comments`, { content, text: content })
    );
  }

  replyToComment(commentId: string, content: string): Observable<CommentItem> {
    return this.withCurrentUser(() =>
      this.http.post<CommentItem>(`/api/comments/${commentId}/replies`, { content, text: content })
    );
  }

  updateComment(commentId: string, content: string): Observable<CommentItem> {
    return this.withCurrentUser(() =>
      this.http.put<CommentItem>(`/api/comments/${commentId}`, { content, text: content })
    );
  }

  deleteComment(commentId: string): Observable<void> {
    return this.withCurrentUser(() =>
      this.http.delete<void>(`/api/comments/${commentId}`)
    );
  }
}
