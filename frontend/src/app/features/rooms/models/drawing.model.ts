export interface DrawingResponse {
  id: string;
  roomId: string;
  author: {
    id: number;
    username: string;
    email?: string;
  };
  kind: 'pen' | 'highlighter' | 'triangle' | 'circle' | 'rectangle';
  color: string;
  width: number;
  opacity: number;
  payload: string;
  offsetX: number;
  offsetY: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface DrawingRequest {
  id?: string;
  kind: string;
  color: string;
  width: number;
  opacity: number;
  payload: string;
  offsetX?: number;
  offsetY?: number;
}

export interface DrawingMoveRequest {
  offsetX: number;
  offsetY: number;
}
