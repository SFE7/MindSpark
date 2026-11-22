import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { beforeAll, describe, expect, it, afterEach, beforeEach, vi } from 'vitest';
import { MediaService } from './media.service';
import { MediaUploadResponse } from './media.model';

describe('MediaService', () => {
  let service: MediaService;
  let httpMock: HttpTestingController;

  beforeAll(() => {
    if (!URL.createObjectURL) {
      URL.createObjectURL = vi.fn().mockReturnValue('blob:mock-uuid');
    }
    if (!URL.revokeObjectURL) {
      URL.revokeObjectURL = vi.fn();
    }
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        MediaService,
      ],
    });

    service = TestBed.inject(MediaService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    service.revokeAll();
    TestBed.resetTestingModule();
  });

  describe('validateClientSide', () => {
    it('returns error when file is missing', () => {
      expect(service.validateClientSide(null as unknown as File)).toBe('No file selected');
    });

    it('returns error when file exceeds 5 MB', () => {
      const largeFile = new File(['x'.repeat(10)], 'test.png', { type: 'image/png' });
      Object.defineProperty(largeFile, 'size', { value: 6 * 1024 * 1024 });
      expect(service.validateClientSide(largeFile)).toBe('File size exceeds the 5 MB maximum limit');
    });

    it('returns error for disallowed MIME types like SVG, PDF, HTML', () => {
      const svgFile = new File(['<svg></svg>'], 'image.svg', { type: 'image/svg+xml' });
      expect(service.validateClientSide(svgFile)).toBe('Unsupported image format. Allowed formats: PNG, JPEG, WebP');

      const pdfFile = new File(['%PDF'], 'doc.pdf', { type: 'application/pdf' });
      expect(service.validateClientSide(pdfFile)).toBe('Unsupported image format. Allowed formats: PNG, JPEG, WebP');
    });

    it('returns null for valid PNG, JPEG, and WebP files', () => {
      const pngFile = new File(['data'], 'test.png', { type: 'image/png' });
      expect(service.validateClientSide(pngFile)).toBeNull();

      const jpegFile = new File(['data'], 'test.jpg', { type: 'image/jpeg' });
      expect(service.validateClientSide(jpegFile)).toBeNull();

      const webpFile = new File(['data'], 'test.webp', { type: 'image/webp' });
      expect(service.validateClientSide(webpFile)).toBeNull();
    });
  });

  describe('uploadRoomImage', () => {
    it('rejects oversized files without HTTP request', () => {
      return new Promise<void>((resolve) => {
        const file = new File([''], 'big.png', { type: 'image/png' });
        Object.defineProperty(file, 'size', { value: 6 * 1024 * 1024 });

        service.uploadRoomImage('room-1', file).subscribe({
          error: (err) => {
            expect(err.message).toContain('5 MB');
            resolve();
          },
        });
      });
    });

    it('uploads valid image via multipart form data', () => {
      const file = new File(['fake-png-data'], 'photo.png', { type: 'image/png' });
      const mockResponse: MediaUploadResponse = {
        mediaId: 'media-uuid-1',
        roomId: 'room-1',
        url: '/api/media/rooms/room-1/media-uuid-1',
        contentType: 'image/png',
        width: 800,
        height: 600,
        sizeBytes: 1024,
      };

      service.uploadRoomImage('room-1', file).subscribe((res) => {
        expect(res).toEqual(mockResponse);
      });

      const req = httpMock.expectOne('/api/media/rooms/room-1');
      expect(req.request.method).toBe('POST');
      expect(req.request.body instanceof FormData).toBe(true);
      req.flush(mockResponse);
    });
  });

  describe('fetchImageBlobUrl and caching', () => {
    it('fetches blob from media endpoint and caches object URL', () => {
      const mockBlob = new Blob(['image-bytes'], { type: 'image/png' });

      service.fetchImageBlobUrl('room-1', 'media-1').subscribe((url) => {
        expect(url).toBeDefined();
        expect(service.getCachedBlobUrl('room-1', 'media-1')).toBe(url);
      });

      const req = httpMock.expectOne('/api/media/rooms/room-1/media-1');
      expect(req.request.method).toBe('GET');
      req.flush(mockBlob);

      // Second call should return cached URL without another HTTP request
      service.fetchImageBlobUrl('room-1', 'media-1').subscribe((cachedUrl) => {
        expect(cachedUrl).toBeDefined();
      });
      httpMock.expectNone('/api/media/rooms/room-1/media-1');
    });

    it('deleteMedia revokes cached blob URL and issues DELETE request', () => {
      service.deleteMedia('room-1', 'media-1').subscribe();

      const req = httpMock.expectOne('/api/media/rooms/room-1/media-1');
      expect(req.request.method).toBe('DELETE');
      req.flush(null);

      expect(service.getCachedBlobUrl('room-1', 'media-1')).toBeNull();
    });
  });
});
