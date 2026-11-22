import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map, of, tap, throwError } from 'rxjs';
import { MediaUploadResponse } from './media.model';

const MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
const ALLOWED_TYPES = ['image/png', 'image/jpeg', 'image/jpg', 'image/webp'];

@Injectable({ providedIn: 'root' })
export class MediaService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/media';

  // In-memory cache: "roomId:mediaId" -> ObjectURL
  private readonly blobUrlCache = new Map<string, string>();

  validateClientSide(file: File): string | null {
    if (!file) return 'No file selected';
    if (file.size > MAX_FILE_SIZE) return 'File size exceeds the 5 MB maximum limit';
    const type = file.type?.toLowerCase();
    if (type && !ALLOWED_TYPES.includes(type)) {
      return 'Unsupported image format. Allowed formats: PNG, JPEG, WebP';
    }
    return null;
  }

  getCachedBlobUrl(roomId: string, mediaId: string): string | null {
    const cacheKey = `${roomId}:${mediaId}`;
    return this.blobUrlCache.get(cacheKey) || null;
  }

  uploadRoomImage(roomId: string, file: File): Observable<MediaUploadResponse> {
    if (!file) {
      return throwError(() => new Error('No file selected'));
    }

    if (file.size > MAX_FILE_SIZE) {
      return throwError(() => new Error('File size exceeds the 5 MB maximum limit'));
    }

    const type = file.type?.toLowerCase();
    if (type && !ALLOWED_TYPES.includes(type)) {
      return throwError(() => new Error('Unsupported image format. Allowed formats: PNG, JPEG, WebP'));
    }

    const formData = new FormData();
    formData.append('file', file);

    return this.http.post<MediaUploadResponse>(`${this.baseUrl}/rooms/${roomId}`, formData);
  }

  fetchImageBlobUrl(roomId: string, mediaId: string): Observable<string> {
    const cacheKey = `${roomId}:${mediaId}`;
    const cached = this.blobUrlCache.get(cacheKey);
    if (cached) {
      return of(cached);
    }

    return this.http.get(`${this.baseUrl}/rooms/${roomId}/${mediaId}`, { responseType: 'blob' }).pipe(
      map((blob) => {
        const objectUrl = URL.createObjectURL(blob);
        this.blobUrlCache.set(cacheKey, objectUrl);
        return objectUrl;
      })
    );
  }

  deleteMedia(roomId: string, mediaId: string): Observable<void> {
    const cacheKey = `${roomId}:${mediaId}`;
    const cached = this.blobUrlCache.get(cacheKey);
    if (cached) {
      try {
        URL.revokeObjectURL(cached);
      } catch {}
      this.blobUrlCache.delete(cacheKey);
    }

    return this.http.delete<void>(`${this.baseUrl}/rooms/${roomId}/${mediaId}`);
  }

  revokeAll(): void {
    for (const url of this.blobUrlCache.values()) {
      try {
        URL.revokeObjectURL(url);
      } catch {}
    }
    this.blobUrlCache.clear();
  }
}
