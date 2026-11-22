import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CanvasImage,
  CreateCanvasImageRequest,
  CreateIdeaAttachmentRequest,
  IdeaAttachment,
  RoomMediaBundle,
  UpdateCanvasImageRequest,
} from './media.model';

@Injectable({ providedIn: 'root' })
export class RoomMediaService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/rooms';

  getRoomAttachments(roomId: string): Observable<RoomMediaBundle> {
    return this.http.get<RoomMediaBundle>(`${this.baseUrl}/${roomId}/attachments`);
  }

  addIdeaAttachment(
    roomId: string,
    ideaId: string,
    req: CreateIdeaAttachmentRequest
  ): Observable<IdeaAttachment> {
    return this.http.post<IdeaAttachment>(
      `${this.baseUrl}/${roomId}/ideas/${ideaId}/attachments`,
      req
    );
  }

  deleteIdeaAttachment(
    roomId: string,
    ideaId: string,
    attachmentId: string
  ): Observable<void> {
    return this.http.delete<void>(
      `${this.baseUrl}/${roomId}/ideas/${ideaId}/attachments/${attachmentId}`
    );
  }

  addCanvasImage(roomId: string, req: CreateCanvasImageRequest): Observable<CanvasImage> {
    return this.http.post<CanvasImage>(`${this.baseUrl}/${roomId}/canvas-images`, req);
  }

  updateCanvasImage(
    roomId: string,
    imageId: string,
    req: UpdateCanvasImageRequest
  ): Observable<CanvasImage> {
    return this.http.patch<CanvasImage>(
      `${this.baseUrl}/${roomId}/canvas-images/${imageId}`,
      req
    );
  }

  deleteCanvasImage(roomId: string, imageId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${roomId}/canvas-images/${imageId}`);
  }
}
