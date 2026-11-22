export interface MediaUploadResponse {
  mediaId: string;
  roomId: string;
  url: string;
  contentType: string;
  width: number;
  height: number;
  sizeBytes: number;
}

export interface IdeaAttachment {
  id: string;
  roomId: string;
  ideaId: string;
  mediaId: string;
  url: string;
  contentType: string;
  width: number;
  height: number;
  uploadedById?: number;
  uploadedBySub?: string;
  uploadedByName?: string;
  createdAt: string;
}

export interface CanvasImage {
  id: string;
  roomId: string;
  mediaId: string;
  url: string;
  contentType: string;
  x: number;
  y: number;
  width: number;
  height: number;
  naturalWidth: number;
  naturalHeight: number;
  zIndex: number;
  uploadedById?: number;
  uploadedBySub?: string;
  uploadedByName?: string;
  createdAt: string;
}

export interface RoomMediaBundle {
  ideaAttachmentsByIdeaId: Record<string, IdeaAttachment[]>;
  canvasImages: CanvasImage[];
}

export interface CreateIdeaAttachmentRequest {
  mediaId: string;
  contentType: string;
  width: number;
  height: number;
}

export interface CreateCanvasImageRequest {
  mediaId: string;
  contentType: string;
  x: number;
  y: number;
  width: number;
  height: number;
  naturalWidth: number;
  naturalHeight: number;
  zIndex?: number;
}

export interface UpdateCanvasImageRequest {
  x: number;
  y: number;
  width: number;
  height: number;
  zIndex?: number;
}
