import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { describe, expect, it, afterEach, beforeEach } from 'vitest';
import { RoomMediaService } from './room-media.service';
import {
  CanvasImage,
  CreateCanvasImageRequest,
  CreateIdeaAttachmentRequest,
  IdeaAttachment,
  RoomMediaBundle,
  UpdateCanvasImageRequest,
} from './media.model';

describe('RoomMediaService', () => {
  let service: RoomMediaService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        RoomMediaService,
      ],
    });

    service = TestBed.inject(RoomMediaService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    TestBed.resetTestingModule();
  });

  it('getRoomAttachments fetches room media bundle', () => {
    const mockBundle: RoomMediaBundle = {
      ideaAttachmentsByIdeaId: {
        'idea-1': [
          {
            id: 'att-1',
            roomId: 'room-1',
            ideaId: 'idea-1',
            mediaId: 'media-1',
            url: '/api/media/rooms/room-1/media-1',
            contentType: 'image/png',
            width: 400,
            height: 300,
            createdAt: '2026-11-21T00:00:00Z',
          },
        ],
      },
      canvasImages: [
        {
          id: 'ci-1',
          roomId: 'room-1',
          mediaId: 'media-2',
          url: '/api/media/rooms/room-1/media-2',
          contentType: 'image/jpeg',
          x: 100,
          y: 150,
          width: 200,
          height: 150,
          naturalWidth: 800,
          naturalHeight: 600,
          zIndex: 1,
          createdAt: '2026-11-21T00:00:00Z',
        },
      ],
    };

    service.getRoomAttachments('room-1').subscribe((res) => {
      expect(res).toEqual(mockBundle);
      expect(res.ideaAttachmentsByIdeaId['idea-1'].length).toBe(1);
      expect(res.canvasImages.length).toBe(1);
    });

    const req = httpMock.expectOne('/api/rooms/room-1/attachments');
    expect(req.request.method).toBe('GET');
    req.flush(mockBundle);
  });

  it('addIdeaAttachment posts to idea attachments endpoint', () => {
    const reqBody: CreateIdeaAttachmentRequest = {
      mediaId: 'media-1',
      contentType: 'image/png',
      width: 600,
      height: 400,
    };
    const mockCreated: IdeaAttachment = {
      id: 'att-new',
      roomId: 'room-1',
      ideaId: 'idea-1',
      url: '/api/media/rooms/room-1/media-1',
      createdAt: '2026-11-21T00:00:00Z',
      ...reqBody,
    };

    service.addIdeaAttachment('room-1', 'idea-1', reqBody).subscribe((res) => {
      expect(res).toEqual(mockCreated);
    });

    const req = httpMock.expectOne('/api/rooms/room-1/ideas/idea-1/attachments');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(reqBody);
    req.flush(mockCreated);
  });

  it('deleteIdeaAttachment deletes specific attachment', () => {
    service.deleteIdeaAttachment('room-1', 'idea-1', 'att-1').subscribe();

    const req = httpMock.expectOne('/api/rooms/room-1/ideas/idea-1/attachments/att-1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('addCanvasImage posts to canvas-images endpoint', () => {
    const reqBody: CreateCanvasImageRequest = {
      mediaId: 'media-ci',
      contentType: 'image/webp',
      x: 200,
      y: 100,
      width: 300,
      height: 200,
      naturalWidth: 600,
      naturalHeight: 400,
      zIndex: 2,
    };
    const mockCreated: CanvasImage = {
      id: 'ci-new',
      roomId: 'room-1',
      url: '/api/media/rooms/room-1/media-ci',
      createdAt: '2026-11-21T00:00:00Z',
      mediaId: 'media-ci',
      contentType: 'image/webp',
      x: 200,
      y: 100,
      width: 300,
      height: 200,
      naturalWidth: 600,
      naturalHeight: 400,
      zIndex: 2,
    };

    service.addCanvasImage('room-1', reqBody).subscribe((res) => {
      expect(res).toEqual(mockCreated);
    });

    const req = httpMock.expectOne('/api/rooms/room-1/canvas-images');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(reqBody);
    req.flush(mockCreated);
  });

  it('updateCanvasImage patches position and dimensions', () => {
    const updateReq: UpdateCanvasImageRequest = {
      x: 350,
      y: 180,
      width: 400,
      height: 300,
      zIndex: 3,
    };
    const mockUpdated: CanvasImage = {
      id: 'ci-1',
      roomId: 'room-1',
      mediaId: 'media-ci',
      url: '/api/media/rooms/room-1/media-ci',
      contentType: 'image/webp',
      naturalWidth: 800,
      naturalHeight: 600,
      createdAt: '2026-11-21T00:00:00Z',
      x: 350,
      y: 180,
      width: 400,
      height: 300,
      zIndex: 3,
    };

    service.updateCanvasImage('room-1', 'ci-1', updateReq).subscribe((res) => {
      expect(res.x).toBe(350);
      expect(res.width).toBe(400);
    });

    const req = httpMock.expectOne('/api/rooms/room-1/canvas-images/ci-1');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(updateReq);
    req.flush(mockUpdated);
  });

  it('deleteCanvasImage deletes canvas image', () => {
    service.deleteCanvasImage('room-1', 'ci-1').subscribe();

    const req = httpMock.expectOne('/api/rooms/room-1/canvas-images/ci-1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
