export interface CanvasPoint {
  x: number;
  y: number;
}

export interface BoundingBox {
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
}

/**
 * Converts screen pixel tolerance (default 6px) to SVG canvas units (0..1000)
 * based on the current rendered surface client width.
 */
export function screenToleranceToCanvasUnits(screenPixels: number, surfaceWidth: number): number {
  if (!surfaceWidth || surfaceWidth <= 0) return 6;
  return screenPixels * (1000 / surfaceWidth);
}

/**
 * Shortest distance from point p to line segment vw.
 */
export function distToSegment(p: CanvasPoint, v: CanvasPoint, w: CanvasPoint): number {
  const l2 = (v.x - w.x) ** 2 + (v.y - w.y) ** 2;
  if (l2 === 0) return Math.hypot(p.x - v.x, p.y - v.y);
  let t = ((p.x - v.x) * (w.x - v.x) + (p.y - v.y) * (w.y - v.y)) / l2;
  t = Math.max(0, Math.min(1, t));
  return Math.hypot(p.x - (v.x + t * (w.x - v.x)), p.y - (v.y + t * (w.y - v.y)));
}

/**
 * Tests if point (in canvas coords) hits a freehand stroke.
 */
export function hitTestStroke(
  point: CanvasPoint,
  points: CanvasPoint[],
  strokeWidth: number,
  tolerance: number,
  offsetX: number = 0,
  offsetY: number = 0
): boolean {
  if (!points || points.length === 0) return false;
  const effectiveTolerance = Math.max(tolerance, strokeWidth / 2 + tolerance);

  if (points.length === 1) {
    const pt = { x: points[0].x + offsetX, y: points[0].y + offsetY };
    return Math.hypot(point.x - pt.x, point.y - pt.y) <= effectiveTolerance;
  }

  for (let i = 0; i < points.length - 1; i++) {
    const p1 = { x: points[i].x + offsetX, y: points[i].y + offsetY };
    const p2 = { x: points[i + 1].x + offsetX, y: points[i + 1].y + offsetY };
    if (distToSegment(point, p1, p2) <= effectiveTolerance) {
      return true;
    }
  }

  return false;
}

/**
 * Tests if point (in canvas coords) hits a shape.
 */
export function hitTestShape(
  point: CanvasPoint,
  shape: { x: number; y: number; width: number; height: number; shape: 'triangle' | 'circle' | 'rectangle'; offsetX?: number; offsetY?: number },
  tolerance: number
): boolean {
  const ox = shape.offsetX || 0;
  const oy = shape.offsetY || 0;
  const x = shape.x + ox;
  const y = shape.y + oy;
  const w = shape.width;
  const h = shape.height;

  if (shape.shape === 'circle') {
    const cx = x + w / 2;
    const cy = y + h / 2;
    const rx = w / 2 + tolerance;
    const ry = h / 2 + tolerance;
    if (rx <= 0 || ry <= 0) return false;
    const normalized = ((point.x - cx) / rx) ** 2 + ((point.y - cy) / ry) ** 2;
    return normalized <= 1;
  }

  if (shape.shape === 'triangle') {
    // 3 vertices of triangle
    const v1 = { x: x + w / 2, y };
    const v2 = { x: x + w, y: y + h };
    const v3 = { x, y: y + h };

    // Point in triangle test (barycentric coordinates)
    const sign = (p1: CanvasPoint, p2: CanvasPoint, p3: CanvasPoint) =>
      (p1.x - p3.x) * (p2.y - p3.y) - (p2.x - p3.x) * (p1.y - p3.y);

    const d1 = sign(point, v1, v2);
    const d2 = sign(point, v2, v3);
    const d3 = sign(point, v3, v1);

    const hasNeg = (d1 < 0) || (d2 < 0) || (d3 < 0);
    const hasPos = (d1 > 0) || (d2 > 0) || (d3 > 0);

    if (!(hasNeg && hasPos)) return true;

    // Check distance to edges for tolerance
    return (
      distToSegment(point, v1, v2) <= tolerance ||
      distToSegment(point, v2, v3) <= tolerance ||
      distToSegment(point, v3, v1) <= tolerance
    );
  }

  // Rectangle
  return (
    point.x >= x - tolerance &&
    point.x <= x + w + tolerance &&
    point.y >= y - tolerance &&
    point.y <= y + h + tolerance
  );
}

/**
 * Computes bounding box for a stroke.
 */
export function getStrokeBoundingBox(points: CanvasPoint[], offsetX: number = 0, offsetY: number = 0): BoundingBox {
  if (!points || points.length === 0) {
    return { minX: offsetX, minY: offsetY, maxX: offsetX, maxY: offsetY };
  }
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;

  for (const pt of points) {
    const x = pt.x + offsetX;
    const y = pt.y + offsetY;
    if (x < minX) minX = x;
    if (y < minY) minY = y;
    if (x > maxX) maxX = x;
    if (y > maxY) maxY = y;
  }

  return { minX, minY, maxX, maxY };
}

/**
 * Computes bounding box for a shape.
 */
export function getShapeBoundingBox(
  shape: { x: number; y: number; width: number; height: number; offsetX?: number; offsetY?: number }
): BoundingBox {
  const ox = shape.offsetX || 0;
  const oy = shape.offsetY || 0;
  return {
    minX: shape.x + ox,
    minY: shape.y + oy,
    maxX: shape.x + shape.width + ox,
    maxY: shape.y + shape.height + oy,
  };
}

/**
 * Simplifies a sequence of points using radial distance threshold.
 */
export function simplifyPoints(points: CanvasPoint[], tolerance: number = 1.5): CanvasPoint[] {
  if (points.length <= 2) return points;
  const result: CanvasPoint[] = [points[0]];
  let prev = points[0];

  for (let i = 1; i < points.length - 1; i++) {
    const pt = points[i];
    if (Math.hypot(pt.x - prev.x, pt.y - prev.y) >= tolerance) {
      result.push(pt);
      prev = pt;
    }
  }

  result.push(points[points.length - 1]);
  return result;
}
