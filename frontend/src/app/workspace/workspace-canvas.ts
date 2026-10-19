import { Component, OnDestroy, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subscription, of, timer } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';

import { MindsparkService, Idea, Room } from '../services/mindspark.service';
import { UserService } from '../services/user-service';

type WorkspaceTool = 'pen' | 'highlighter' | 'shapes' | 'idea' | 'eraser';
type WorkspaceShape = 'triangle' | 'circle' | 'rectangle';

interface ToolOption {
  id: WorkspaceTool;
  label: string;
  iconPath: string;
}

interface ColorOption {
  name: string;
  value: string;
}

interface ShapeOption {
  id: WorkspaceShape;
  iconPath: string;
}

interface CanvasPoint {
  x: number;
  y: number;
}

interface StrokeItem {
  kind: 'stroke';
  id: number;
  points: CanvasPoint[];
  color: string;
  width: number;
  opacity: number;
}

interface ShapeItem {
  kind: 'shape';
  id: number;
  shape: WorkspaceShape;
  x: number;
  y: number;
  width: number;
  height: number;
  color: string;
}

export interface IdeaItem {
  kind: 'idea';
  id: string;
  x: number;
  y: number;
  color: string;
  creatorId: number | null;
  title: string;
  description: string;
  voteCount?: number;
  voted?: boolean;
}

type CanvasItem = StrokeItem | ShapeItem | IdeaItem;

@Component({
  selector: 'app-workspace-canvas',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './workspace-canvas.html',
  styleUrl: './workspace-canvas.scss',
})
export class WorkspaceCanvasComponent implements OnDestroy {
  readonly roomId = input<string>('');
  readonly room = input<Room | null>(null);
  readonly leave = output<void>();

  private readonly mindspark = inject(MindsparkService);
  private readonly userService = inject(UserService);
  private readonly router = inject(Router);

  protected goHome(): void {
    void this.router.navigate(['/']);
  }

  protected onLeave(): void {
    this.leave.emit();
  }

  protected readonly tools: ToolOption[] = [
    { id: 'pen', label: 'Pen', iconPath: '/assets/mindspark/pen.png' },
    { id: 'highlighter', label: 'Highlighter', iconPath: '/assets/mindspark/highlighter.png' },
    { id: 'shapes', label: 'Shapes', iconPath: '/assets/mindspark/shapes.png' },
    { id: 'idea', label: 'Idea', iconPath: '/assets/mindspark/idea.png' },
    { id: 'eraser', label: 'Eraser', iconPath: '/assets/mindspark/eraser.png' },
  ];

  protected readonly shapes: ShapeOption[] = [
    { id: 'triangle', iconPath: '/assets/mindspark/triangle.png' },
    { id: 'circle', iconPath: '/assets/mindspark/circle.png' },
    { id: 'rectangle', iconPath: '/assets/mindspark/rectangle.png' },
  ];

  protected readonly colors: ColorOption[] = [
    { name: 'Ink', value: '#273b3b' },
    { name: 'Coral', value: '#ed8d74' },
    { name: 'Butter', value: '#f3c96b' },
    { name: 'Sage', value: '#9fc9a9' },
    { name: 'Sky', value: '#9dc7d8' },
    { name: 'Lilac', value: '#b9add5' },
  ];

  protected readonly selectedTool = signal<WorkspaceTool>('pen');
  protected readonly selectedShape = signal<WorkspaceShape>('rectangle');
  protected readonly selectedColor = signal(this.colors[0].value);
  protected readonly shapesOpen = signal(false);
  protected readonly items = signal<CanvasItem[]>([]);
  protected readonly preview = signal<CanvasItem | null>(null);
  protected readonly ideaDraft = signal<{ id?: string; x: number; y: number; title: string; description: string } | null>(null);
  protected readonly ideaError = signal('');
  protected readonly ideaList = computed(() => this.items().filter((item): item is IdeaItem => item.kind === 'idea'));
  protected readonly canUndo = computed(() => this.historyIndex() > 0);
  protected readonly canRedo = computed(() => this.historyIndex() < this.history.length - 1);
  protected readonly draggingIdea = signal<IdeaItem | null>(null);

  private nextItemId = 1;
  private history: CanvasItem[][] = [[]];
  private readonly historyIndex = signal(0);
  private pointerId: number | null = null;
  private startPoint: CanvasPoint | null = null;
  private draftPoints: CanvasPoint[] = [];
  private actionStartItems: CanvasItem[] | null = null;
  private pollSub?: Subscription;

  private activeDragPointerId: number | null = null;
  private dragStartPointer = { x: 0, y: 0 };
  private dragStartIdeaPos = { x: 0, y: 0 };
  private hasDragged = false;
  private localPositions = new Map<string, { x: number; y: number }>();

  constructor() {
    effect(() => {
      const currentRoomId = this.roomId();
      if (currentRoomId) {
        this.loadLocalPositions(currentRoomId);
        this.startSync(currentRoomId);
      } else {
        this.stopSync();
      }
    });
  }

  ngOnDestroy(): void {
    this.stopSync();
  }

  private loadLocalPositions(roomId: string): void {
    try {
      const saved = localStorage.getItem(`mindspark-pos-${roomId}`);
      if (saved) {
        const parsed = JSON.parse(saved) as Record<string, { x: number; y: number }>;
        this.localPositions = new Map(Object.entries(parsed));
      }
    } catch {}
  }

  private saveLocalPositions(): void {
    const id = this.roomId();
    if (!id) return;
    try {
      const obj = Object.fromEntries(this.localPositions.entries());
      localStorage.setItem(`mindspark-pos-${id}`, JSON.stringify(obj));
    } catch {}
  }

  private startSync(currentRoomId: string): void {
    this.stopSync();
    // Fetch immediately, then poll every 2000ms so new ideas appear to all participants
    this.pollSub = timer(0, 2000)
      .pipe(
        switchMap(() =>
          this.mindspark.listIdeas(currentRoomId).pipe(
            catchError(err => {
              console.error('Error fetching room ideas:', err);
              return of(null);
            })
          )
        )
      )
      .subscribe(ideas => {
        if (ideas) {
          this.syncServerIdeas(ideas);
        }
      });
  }

  private stopSync(): void {
    if (this.pollSub) {
      this.pollSub.unsubscribe();
      this.pollSub = undefined;
    }
  }

  private syncServerIdeas(ideas: Idea[]): void {
    const currentItems = this.items();
    const nonIdeas = currentItems.filter(item => item.kind !== 'idea');
    const currentlyDragging = this.draggingIdea();

    const serverIdeas: IdeaItem[] = ideas.map(idea => {
      const isBeingDragged = currentlyDragging !== null && currentlyDragging.id === idea.id;
      const currentLocal = currentItems.find(i => i.id === idea.id) as IdeaItem | undefined;
      const cached = this.localPositions.get(idea.id);

      let x: number;
      let y: number;

      if (isBeingDragged && currentLocal) {
        x = currentLocal.x;
        y = currentLocal.y;
      } else if (cached) {
        // If we have a local cached position from dragging, prioritize it unless server has an updated position
        if (idea.x != null && idea.y != null && (idea.x !== 500 || idea.y !== 250)) {
          x = idea.x;
          y = idea.y;
          this.localPositions.set(idea.id, { x, y });
        } else {
          x = cached.x;
          y = cached.y;
        }
      } else if (idea.x != null && idea.y != null) {
        x = idea.x;
        y = idea.y;
        this.localPositions.set(idea.id, { x, y });
      } else if (currentLocal) {
        x = currentLocal.x;
        y = currentLocal.y;
      } else {
        x = 500;
        y = 250;
      }

      return {
        kind: 'idea',
        id: idea.id,
        x,
        y,
        color: idea.color || '#9fc9a9',
        creatorId: idea.author.id,
        title: idea.title || idea.text || 'Untitled Idea',
        description: idea.description || '',
        voteCount: idea.voteCount ?? 0,
        voted: !!idea.voted,
      };
    });

    const currentIdeas = this.ideaList();
    const isDifferent =
      currentIdeas.length !== serverIdeas.length ||
      serverIdeas.some((si, index) => {
        const ci = currentIdeas[index];
        return (
          !ci ||
          ci.id !== si.id ||
          ci.title !== si.title ||
          ci.description !== si.description ||
          ci.x !== si.x ||
          ci.y !== si.y ||
          ci.color !== si.color ||
          ci.creatorId !== si.creatorId ||
          ci.voteCount !== si.voteCount ||
          ci.voted !== si.voted
        );
      });

    if (isDifferent) {
      this.items.set([...nonIdeas, ...serverIdeas]);
    }
  }

  protected selectTool(tool: WorkspaceTool): void {
    this.selectedTool.set(tool);
    this.shapesOpen.set(tool === 'shapes');
  }

  protected selectShape(shape: ShapeOption): void {
    this.selectedShape.set(shape.id);
    this.selectedTool.set('shapes');
    this.shapesOpen.set(false);
  }

  protected selectColor(color: string): void {
    this.selectedColor.set(color);
  }

  protected undo(): void {
    if (!this.canUndo()) return;
    this.historyIndex.update(index => index - 1);
    this.items.set(this.history[this.historyIndex()]);
    this.preview.set(null);
  }

  protected redo(): void {
    if (!this.canRedo()) return;
    this.historyIndex.update(index => index + 1);
    this.items.set(this.history[this.historyIndex()]);
    this.preview.set(null);
  }

  protected onPointerDown(event: PointerEvent): void {
    const surface = event.currentTarget as SVGElement;
    surface.setPointerCapture(event.pointerId);
    this.pointerId = event.pointerId;
    const point = this.toCanvasPoint(event, surface);
    this.startPoint = point;
    this.actionStartItems = this.items();

    if (this.selectedTool() === 'idea') {
      this.ideaDraft.set({ x: point.x, y: point.y, title: '', description: '' });
      this.ideaError.set('');
      this.finishPointer(surface);
      return;
    }

    if (this.selectedTool() === 'eraser') {
      this.eraseAt(point);
      return;
    }

    if (this.selectedTool() === 'pen' || this.selectedTool() === 'highlighter') {
      this.draftPoints = [point];
      this.preview.set(this.createStroke());
    }
  }

  protected onPointerMove(event: PointerEvent): void {
    if (event.pointerId !== this.pointerId) return;
    const surface = event.currentTarget as SVGElement;
    const point = this.toCanvasPoint(event, surface);

    if (this.selectedTool() === 'eraser') {
      this.eraseAt(point);
      return;
    }

    if (!this.startPoint) return;
    if (this.selectedTool() === 'pen' || this.selectedTool() === 'highlighter') {
      this.draftPoints = [...this.draftPoints, point];
      this.preview.set(this.createStroke());
    } else if (this.selectedTool() === 'shapes') {
      this.preview.set(this.createShape(this.startPoint, point));
    }
  }

  protected onPointerUp(event: PointerEvent): void {
    if (event.pointerId !== this.pointerId) return;
    const surface = event.currentTarget as SVGElement;
    const currentPreview = this.preview();
    if (currentPreview && this.selectedTool() !== 'eraser') {
      this.commit([...this.items(), currentPreview]);
    } else if (this.selectedTool() === 'eraser' && this.actionStartItems !== this.items()) {
      this.commit(this.items());
    }
    this.finishPointer(surface);
  }

  protected onIdeaPointerDown(event: PointerEvent, idea: IdeaItem): void {
    if (event.button !== 0) return;

    if (this.selectedTool() === 'eraser') {
      event.stopPropagation();
      if (this.isCreator(idea)) {
        this.eraseIdea(idea);
      }
      return;
    }

    event.stopPropagation();
    const cardEl = event.currentTarget as HTMLElement;
    cardEl.setPointerCapture(event.pointerId);
    this.activeDragPointerId = event.pointerId;
    this.draggingIdea.set(idea);
    this.dragStartPointer = { x: event.clientX, y: event.clientY };
    this.dragStartIdeaPos = { x: idea.x, y: idea.y };
    this.hasDragged = false;
  }

  protected onIdeaPointerMove(event: PointerEvent): void {
    if (this.activeDragPointerId !== event.pointerId || !this.draggingIdea()) return;

    const dxPixels = event.clientX - this.dragStartPointer.x;
    const dyPixels = event.clientY - this.dragStartPointer.y;

    if (!this.hasDragged && Math.hypot(dxPixels, dyPixels) > 3) {
      this.hasDragged = true;
    }

    if (this.hasDragged) {
      const stage = (event.currentTarget as HTMLElement).closest('.canvas-stage') as HTMLElement | null;
      const bounds = stage ? stage.getBoundingClientRect() : { width: 1000, height: 500 };
      const dxCanvas = (dxPixels / bounds.width) * 1000;
      const dyCanvas = (dyPixels / bounds.height) * 500;

      const newX = Math.round(Math.max(30, Math.min(970, this.dragStartIdeaPos.x + dxCanvas)));
      const newY = Math.round(Math.max(20, Math.min(480, this.dragStartIdeaPos.y + dyCanvas)));

      this.items.update(items =>
        items.map(item =>
          item.kind === 'idea' && item.id === this.draggingIdea()!.id
            ? { ...item, x: newX, y: newY }
            : item
        )
      );
    }
  }

  protected onIdeaPointerUp(event: PointerEvent): void {
    if (this.activeDragPointerId !== event.pointerId) return;

    const cardEl = event.currentTarget as HTMLElement;
    try {
      cardEl.releasePointerCapture(event.pointerId);
    } catch {}

    const dragged = this.draggingIdea();
    const wasDragged = this.hasDragged;

    this.draggingIdea.set(null);
    this.activeDragPointerId = null;
    this.hasDragged = false;

    if (!wasDragged) {
      if (dragged && this.isCreator(dragged)) {
        this.editIdea(dragged);
      }
      return;
    }

    if (dragged) {
      const currentItem = this.items().find(i => i.id === dragged.id);
      if (currentItem && currentItem.kind === 'idea') {
        // Record in local cache immediately so background polling never snaps it back
        this.localPositions.set(dragged.id, { x: currentItem.x, y: currentItem.y });
        this.saveLocalPositions();

        if (this.roomId()) {
          this.mindspark
            .updateIdea(dragged.id, {
              title: currentItem.title,
              description: currentItem.description,
              x: currentItem.x,
              y: currentItem.y,
              color: currentItem.color,
            })
            .subscribe({
              next: updated => {
                if (updated.x != null && updated.y != null) {
                  this.localPositions.set(updated.id, { x: updated.x, y: updated.y });
                  this.saveLocalPositions();
                }
              },
              error: err => console.error('Could not save dragged idea position', err),
            });
        }
      }
    }
  }

  private eraseIdea(idea: IdeaItem): void {
    this.localPositions.delete(idea.id);
    this.saveLocalPositions();

    if (this.roomId()) {
      this.mindspark.deleteIdea(idea.id).subscribe({
        error: () => console.error('Could not delete idea', idea.id),
      });
    }
    this.items.update(items => items.filter(item => item.id !== idea.id));
  }

  protected strokePath(points: CanvasPoint[]): string {
    return points.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`).join(' ');
  }

  protected trianglePoints(item: ShapeItem): string {
    const centerX = item.x + item.width / 2;
    return `${centerX},${item.y} ${item.x + item.width},${item.y + item.height} ${item.x},${item.y + item.height}`;
  }

  protected isStroke(item: CanvasItem): item is StrokeItem {
    return item.kind === 'stroke';
  }

  protected isShape(item: CanvasItem): item is ShapeItem {
    return item.kind === 'shape';
  }

  protected isIdea(item: CanvasItem): item is IdeaItem {
    return item.kind === 'idea';
  }

  protected saveIdea(): void {
    const draft = this.ideaDraft();
    if (!draft) return;
    const title = draft.title.trim();
    if (!title) {
      this.ideaError.set('A title is required.');
      return;
    }

    const currentRoomId = this.roomId();
    const description = draft.description.trim();

    if (draft.id === undefined) {
      if (!currentRoomId) {
        const localIdea: IdeaItem = {
          kind: 'idea',
          id: String(this.nextItemId++),
          x: draft.x,
          y: draft.y,
          color: this.selectedColor(),
          creatorId: this.userService.currentUser()?.id ?? null,
          title,
          description,
          voteCount: 0,
          voted: false,
        };
        this.localPositions.set(localIdea.id, { x: draft.x, y: draft.y });
        this.saveLocalPositions();
        this.commit([...this.items(), localIdea]);
        this.ideaDraft.set(null);
        this.ideaError.set('');
        return;
      }

      this.mindspark
        .createIdea(currentRoomId, {
          title,
          description,
          x: draft.x,
          y: draft.y,
          color: this.selectedColor(),
        })
        .subscribe({
          next: newIdea => {
            const ideaItem: IdeaItem = {
              kind: 'idea',
              id: newIdea.id,
              x: newIdea.x ?? draft.x,
              y: newIdea.y ?? draft.y,
              color: newIdea.color || this.selectedColor(),
              creatorId: newIdea.author.id,
              title: newIdea.title || newIdea.text || title,
              description: newIdea.description || description,
              voteCount: newIdea.voteCount ?? 0,
              voted: !!newIdea.voted,
            };
            this.localPositions.set(ideaItem.id, { x: ideaItem.x, y: ideaItem.y });
            this.saveLocalPositions();
            this.commit([...this.items(), ideaItem]);
            this.ideaDraft.set(null);
            this.ideaError.set('');
          },
          error: () => {
            this.ideaError.set('Could not save idea to server.');
          },
        });
    } else {
      const ideaId = draft.id;
      if (!currentRoomId) {
        this.commit(
          this.items().map(item =>
            item.kind === 'idea' && item.id === ideaId ? { ...item, title, description } : item
          )
        );
        this.ideaDraft.set(null);
        this.ideaError.set('');
        return;
      }

      this.mindspark.updateIdea(ideaId, { title, description }).subscribe({
        next: updated => {
          this.commit(
            this.items().map(item =>
              item.kind === 'idea' && item.id === ideaId
                ? {
                    ...item,
                    title: updated.title || updated.text || title,
                    description: updated.description || description,
                  }
                : item
            )
          );
          this.ideaDraft.set(null);
          this.ideaError.set('');
        },
        error: () => {
          this.ideaError.set('Could not update idea.');
        },
      });
    }
  }

  protected editIdea(idea: IdeaItem): void {
    if (!this.isCreator(idea)) return;
    this.ideaDraft.set({ id: idea.id, x: idea.x, y: idea.y, title: idea.title, description: idea.description });
    this.ideaError.set('');
  }

  protected isCreator(idea: IdeaItem): boolean {
    const currentUserId = this.userService.currentUser()?.id;
    return currentUserId !== undefined && idea.creatorId === currentUserId;
  }

  protected cancelIdea(): void {
    this.ideaDraft.set(null);
    this.ideaError.set('');
  }

  protected updateIdeaDraft(field: 'title' | 'description', value: string): void {
    this.ideaDraft.update(draft => (draft ? { ...draft, [field]: value } : draft));
  }

  protected toggleVote(event: Event, idea: IdeaItem): void {
    event.stopPropagation();
    if (!idea.id || !this.roomId()) return;

    const request$ = idea.voted ? this.mindspark.removeVote(idea.id) : this.mindspark.vote(idea.id);

    request$.subscribe({
      next: updated => {
        this.items.update(items =>
          items.map(item =>
            item.kind === 'idea' && item.id === idea.id
              ? { ...item, voteCount: updated.voteCount, voted: updated.voted }
              : item
          )
        );
      },
      error: () => {
        console.error('Could not toggle vote');
      },
    });
  }

  protected percentX(value: number): string {
    return `${value / 10}%`;
  }

  protected percentY(value: number): string {
    return `${value / 5}%`;
  }

    private createStroke(): StrokeItem {
      const highlighter = this.selectedTool() === 'highlighter';
      return {
        kind: 'stroke',
        id: this.nextItemId++,
        points: this.draftPoints,
        color: this.selectedColor(),
        width: highlighter ? 30 : 5,
        opacity: highlighter ? 0.35 : 1,
      };
    }

  private createShape(start: CanvasPoint, end: CanvasPoint): ShapeItem {
    return {
      kind: 'shape',
      id: this.nextItemId++,
      shape: this.selectedShape(),
      x: Math.min(start.x, end.x),
      y: Math.min(start.y, end.y),
      width: Math.max(Math.abs(end.x - start.x), 12),
      height: Math.max(Math.abs(end.y - start.y), 12),
      color: this.selectedColor(),
    };
  }

  private eraseAt(point: CanvasPoint): void {
    const currentRoomId = this.roomId();
    const erasedIdeas = this.items().filter(
      (item): item is IdeaItem => item.kind === 'idea' && this.isNear(item, point) && this.isCreator(item)
    );

    if (erasedIdeas.length > 0 && currentRoomId) {
      for (const erased of erasedIdeas) {
        this.localPositions.delete(erased.id);
        this.mindspark.deleteIdea(erased.id).subscribe({
          error: () => console.error('Could not delete idea from server', erased.id),
        });
      }
      this.saveLocalPositions();
    }

    this.items.update(items =>
      items.filter(item => !this.isNear(item, point) || (item.kind === 'idea' && !this.isCreator(item)))
    );
  }

  private isNear(item: CanvasItem, point: CanvasPoint): boolean {
    if (item.kind === 'idea') {
      return Math.abs(item.x - point.x) < 70 && Math.abs(item.y - point.y) < 35;
    }
    if (item.kind === 'shape') {
      return (
        point.x >= item.x - 12 &&
        point.x <= item.x + item.width + 12 &&
        point.y >= item.y - 12 &&
        point.y <= item.y + item.height + 12
      );
    }
    return item.points.some(strokePoint => Math.hypot(strokePoint.x - point.x, strokePoint.y - point.y) < 24);
  }

  private toCanvasPoint(event: PointerEvent, surface: SVGElement): CanvasPoint {
    const bounds = surface.getBoundingClientRect();
    return {
      x: ((event.clientX - bounds.left) / bounds.width) * 1000,
      y: ((event.clientY - bounds.top) / bounds.height) * 500,
    };
  }

  private finishPointer(surface: SVGElement): void {
    if (this.pointerId !== null) surface.releasePointerCapture(this.pointerId);
    this.pointerId = null;
    this.startPoint = null;
    this.draftPoints = [];
    this.actionStartItems = null;
    this.preview.set(null);
  }

  private commit(items: CanvasItem[]): void {
    this.history = [...this.history.slice(0, this.historyIndex() + 1), items];
    this.historyIndex.set(this.historyIndex() + 1);
    this.items.set(items);
  }
}
