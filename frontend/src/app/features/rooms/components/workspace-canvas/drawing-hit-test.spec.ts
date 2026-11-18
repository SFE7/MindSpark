import { describe, it, expect } from 'vitest';
import {
  screenToleranceToCanvasUnits,
  distToSegment,
  hitTestStroke,
  hitTestShape,
  getStrokeBoundingBox,
  getShapeBoundingBox,
  simplifyPoints,
} from './drawing-hit-test';

describe('Drawing Hit Testing & Math Utilities', () => {
  it('converts screen pixel tolerance to canvas units based on surface width', () => {
    // 1000 viewBox units on 1000px screen => 1:1 => 6px = 6 canvas units
    expect(screenToleranceToCanvasUnits(6, 1000)).toBe(6);
    // 1000 viewBox units on 2000px screen => 6px = 3 canvas units
    expect(screenToleranceToCanvasUnits(6, 2000)).toBe(3);
    // 1000 viewBox units on 500px screen => 6px = 12 canvas units
    expect(screenToleranceToCanvasUnits(6, 500)).toBe(12);
  });

  it('calculates distance from point to segment correctly', () => {
    const v = { x: 0, y: 0 };
    const w = { x: 10, y: 0 };

    // Point exactly on segment
    expect(distToSegment({ x: 5, y: 0 }, v, w)).toBe(0);
    // Point perpendicular to segment midpoint
    expect(distToSegment({ x: 5, y: 5 }, v, w)).toBe(5);
    // Point beyond segment endpoint v
    expect(distToSegment({ x: -3, y: 4 }, v, w)).toBe(5);
    // Point beyond segment endpoint w
    expect(distToSegment({ x: 13, y: 4 }, v, w)).toBe(5);
  });

  it('hit tests strokes with offset and width tolerance', () => {
    const points = [
      { x: 10, y: 10 },
      { x: 50, y: 10 },
    ];
    // Exact hit
    expect(hitTestStroke({ x: 30, y: 10 }, points, 4, 2)).toBe(true);
    // Within tolerance (dist 3 <= width/2 (2) + tolerance (2) = 4)
    expect(hitTestStroke({ x: 30, y: 13 }, points, 4, 2)).toBe(true);
    // Outside tolerance (dist 5 > 4)
    expect(hitTestStroke({ x: 30, y: 16 }, points, 4, 2)).toBe(false);

    // With offset X=10, Y=20
    expect(hitTestStroke({ x: 40, y: 30 }, points, 4, 2, 10, 20)).toBe(true);
  });

  it('hit tests shapes: rectangle, circle, triangle', () => {
    // Rectangle
    const rect = { x: 100, y: 100, width: 50, height: 40, shape: 'rectangle' as const };
    expect(hitTestShape({ x: 120, y: 120 }, rect, 5)).toBe(true);
    expect(hitTestShape({ x: 153, y: 120 }, rect, 5)).toBe(true); // within 5px tolerance
    expect(hitTestShape({ x: 200, y: 200 }, rect, 5)).toBe(false);

    // Circle
    const circle = { x: 100, y: 100, width: 50, height: 50, shape: 'circle' as const };
    // Center is (125, 125)
    expect(hitTestShape({ x: 125, y: 125 }, circle, 5)).toBe(true);
    expect(hitTestShape({ x: 100, y: 100 }, circle, 5)).toBe(false); // Corner of bounding box outside circle

    // Triangle
    const triangle = { x: 100, y: 100, width: 60, height: 60, shape: 'triangle' as const };
    // Near center
    expect(hitTestShape({ x: 130, y: 140 }, triangle, 5)).toBe(true);
    // Outside top-left corner
    expect(hitTestShape({ x: 102, y: 102 }, triangle, 2)).toBe(false);
  });

  it('computes bounding boxes for strokes and shapes', () => {
    const strokeBox = getStrokeBoundingBox([
      { x: 10, y: 20 },
      { x: 50, y: 80 },
      { x: 5, y: 30 },
    ], 10, 5);
    expect(strokeBox).toEqual({ minX: 15, minY: 25, maxX: 60, maxY: 85 });

    const shapeBox = getShapeBoundingBox({ x: 100, y: 200, width: 80, height: 50, offsetX: 10, offsetY: -5 });
    expect(shapeBox).toEqual({ minX: 110, minY: 195, maxX: 190, maxY: 245 });
  });

  it('simplifies point stream to reduce storage overhead', () => {
    const densePoints = [
      { x: 0, y: 0 },
      { x: 0.2, y: 0.1 },
      { x: 0.4, y: 0.2 },
      { x: 5, y: 5 },
      { x: 5.1, y: 5.2 },
      { x: 10, y: 10 },
    ];
    const simplified = simplifyPoints(densePoints, 1.5);
    expect(simplified.length).toBeLessThan(densePoints.length);
    expect(simplified[0]).toEqual({ x: 0, y: 0 });
    expect(simplified[simplified.length - 1]).toEqual({ x: 10, y: 10 });
  });
});
