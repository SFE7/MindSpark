import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, switchMap } from 'rxjs';
import { UserService } from '../../../core/user-service';
import { DrawingMoveRequest, DrawingRequest, DrawingResponse } from '../models/drawing.model';

@Injectable({ providedIn: 'root' })
export class DrawingsService {
  private readonly http = inject(HttpClient);
  private readonly userService = inject(UserService);

  private withCurrentUser<T>(request: () => Observable<T>): Observable<T> {
    return this.userService.syncUser().pipe(
      switchMap(() => request())
    );
  }

  getRoomDrawings(roomId: string): Observable<DrawingResponse[]> {
    return this.withCurrentUser(() =>
      this.http.get<DrawingResponse[]>(`/api/rooms/${roomId}/drawings`)
    );
  }

  createDrawing(roomId: string, req: DrawingRequest): Observable<DrawingResponse> {
    return this.withCurrentUser(() =>
      this.http.post<DrawingResponse>(`/api/rooms/${roomId}/drawings`, req)
    );
  }

  moveDrawing(roomId: string, drawingId: string, req: DrawingMoveRequest): Observable<DrawingResponse> {
    return this.withCurrentUser(() =>
      this.http.put<DrawingResponse>(`/api/rooms/${roomId}/drawings/${drawingId}`, req)
    );
  }

  deleteDrawing(roomId: string, drawingId: string): Observable<void> {
    return this.withCurrentUser(() =>
      this.http.delete<void>(`/api/rooms/${roomId}/drawings/${drawingId}`)
    );
  }
}
