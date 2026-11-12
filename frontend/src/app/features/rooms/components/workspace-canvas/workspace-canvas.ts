import { Component, OnDestroy, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subscription, forkJoin, of, timer } from 'rxjs';
import { catchError, exhaustMap, timeout } from 'rxjs/operators';

import { CategoriesService } from '../../data-access/categories.service';
import { IdeasService } from '../../data-access/ideas.service';
import { RoomsService } from '../../data-access/rooms.service';
import { Category } from '../../models/category.model';
import { Idea } from '../../models/idea.model';
import { IdeaFilters, IdeaSortBy, Room } from '../../models/room.model';
import { User } from '../../../../core/models/User';
import { UserService } from '../../../../core/user-service';

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
  categoryId: number | null;
  categoryTitle: string | null;
  category?: { id: number; title: string } | null;
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
  readonly roomChanged = output<Room>();

  private readonly mindspark = inject(IdeasService);
  private readonly roomsService = inject(RoomsService);
  private readonly categoriesService = inject(CategoriesService);
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
  protected readonly ideaDraft = signal<{ id?: string; x: number; y: number; title: string; description: string; categoryId: number | null } | null>(null);
  protected readonly ideaError = signal('');
  protected readonly ideaList = computed(() => this.items().filter((item): item is IdeaItem => item.kind === 'idea'));
  protected readonly canUndo = computed(() => this.historyIndex() > 0);
  protected readonly canRedo = computed(() => this.historyIndex() < this.history.length - 1);
  protected readonly draggingIdea = signal<IdeaItem | null>(null);
  protected readonly isEditingRoomName = signal(false);
  protected readonly editingRoomName = signal('');
  protected readonly roomNameError = signal('');
  protected readonly participantsOpen = signal(false);

  // ---------- Categories, search, filter and sort (Sprint 2 - phase 2) ----------
  protected readonly categories = signal<Category[]>([]);
  protected readonly categoriesOpen = signal(false);
  protected readonly newCategoryTitle = signal('');
  protected readonly editingCategoryId = signal<number | null>(null);
  protected readonly editingCategoryTitle = signal('');
  protected readonly categoryError = signal('');

  protected readonly searchInput = signal('');
  private readonly searchTerm = signal('');
  protected readonly filterCategoryId = signal<number | null>(null);
  protected readonly selectedCategoryId = computed(() => this.filterCategoryId());
  protected readonly sortBy = signal<IdeaSortBy>('createdAt');
  private searchTimer?: ReturnType<typeof setTimeout>;

  // Every change of these signals triggers a new call to IdeasService.listIdeas()
  private readonly filters = computed<IdeaFilters>(() => ({
    categoryId: this.filterCategoryId(),
    search: this.searchTerm(),
    sortBy: this.sortBy(),
  }));
  protected readonly hasActiveFilters = computed(
    () => this.filterCategoryId() !== null || this.searchTerm() !== ''
  );
  // Only the room owner can create / rename / delete categories (backend rule) and only while the room is open
  protected readonly canManageCategories = computed(() => this.isRoomOwner() && !this.room()?.closed);

  protected onSearchInput(value: string): void {
    this.searchInput.set(value);
    clearTimeout(this.searchTimer);
    // debounce so we do not call the backend on every keystroke
    this.searchTimer = setTimeout(() => this.searchTerm.set(value.trim()), 300);
  }

  protected clearSearch(): void {
    clearTimeout(this.searchTimer);
    this.searchInput.set('');
    this.searchTerm.set('');
  }

  protected onFilterCategory(categoryId: number | null): void {
    this.filterCategoryId.set(categoryId);
  }

  protected setSort(sortBy: IdeaSortBy): void {
    this.sortBy.set(sortBy);
  }

  protected selectCategoryFilter(categoryId: number | null): void {
    this.onFilterCategory(categoryId);
  }

  protected toggleSort(): void {
    this.setSort(this.sortBy() === 'createdAt' ? 'voteCount' : 'createdAt');
  }

  protected resetFilters(): void {
    this.clearSearch();
    this.filterCategoryId.set(null);
  }

  protected toggleCategories(): void {
    this.categoriesOpen.update(v => !v);
    this.categoryError.set('');
    this.editingCategoryId.set(null);
  }

  protected startEditingCategory(category: Category): void {
    if (!this.canManageCategories()) return;
    this.editingCategoryId.set(category.id);
    this.editingCategoryTitle.set(category.title);
    this.categoryError.set('');
  }

  protected cancelEditingCategory(): void {
    this.editingCategoryId.set(null);
    this.categoryError.set('');
  }

  protected addCategory(): void {
    const currentRoomId = this.roomId();
    if (!currentRoomId || !this.canManageCategories()) return;

    const title = this.newCategoryTitle().trim();
    if (!title) {
      this.categoryError.set('Category name is required.');
      return;
    }
    if (this.categories().some(c => c.title.toLowerCase() === title.toLowerCase())) {
      this.categoryError.set('This category already exists.');
      return;
    }

    this.categoriesService.createCategory(currentRoomId, title).subscribe({
      next: created => {
        this.categories.update(list => [...list, created]);
        this.newCategoryTitle.set('');
        this.categoryError.set('');
      },
      error: err => this.categoryError.set(this.categoryErrorMessage(err, 'Could not create the category.')),
    });
  }

  protected saveCategory(): void {
    const categoryId = this.editingCategoryId();
    if (categoryId === null || !this.canManageCategories()) return;

    const title = this.editingCategoryTitle().trim();
    if (!title) {
      this.categoryError.set('Category name is required.');
      return;
    }

    this.categoriesService.updateCategory(categoryId, title).subscribe({
      next: updated => {
        this.categories.update(list => list.map(c => (c.id === updated.id ? updated : c)));
        // refresh the badge shown on the cards right away
        this.items.update(items =>
          items.map(item =>
            item.kind === 'idea' && item.categoryId === updated.id
              ? { ...item, categoryTitle: updated.title }
              : item
          )
        );
        this.editingCategoryId.set(null);
        this.categoryError.set('');
      },
      error: err => this.categoryError.set(this.categoryErrorMessage(err, 'Could not rename the category.')),
    });
  }

  protected deleteCategory(category: Category): void {
    if (!this.canManageCategories()) return;

    const confirmed = window.confirm(
      `Delete the category "${category.title}"? Its ideas will become uncategorized.`
    );
    if (!confirmed) return;

    this.categoriesService.deleteCategory(category.id).subscribe({
      next: () => {
        this.categories.update(list => list.filter(c => c.id !== category.id));
        if (this.filterCategoryId() === category.id) {
          this.filterCategoryId.set(null);
        }
        // the backend sets the category of its ideas to null
        this.items.update(items =>
          items.map(item =>
            item.kind === 'idea' && item.categoryId === category.id
              ? { ...item, categoryId: null, categoryTitle: null }
              : item
          )
        );
        this.categoryError.set('');
      },
      error: err => this.categoryError.set(this.categoryErrorMessage(err, 'Could not delete the category.')),
    });
  }

  private categoryErrorMessage(err: unknown, fallback: string): string {
    const status = (err as { status?: number } | null)?.status;
    if (status === 409) return 'A category with this name already exists in this room.';
    if (status === 403) return 'Only the room owner can manage categories.';
    if (status === 400) return 'The category name cannot be empty.';
    return fallback;
  }

  private categoryTitleOf(categoryId: number | null): string | null {
    if (categoryId === null) return null;
    return this.categories().find(c => c.id === categoryId)?.title ?? null;
  }

  private syncCategories(categories: Category[]): void {
    const current = this.categories();
    const changed =
      current.length !== categories.length ||
      categories.some((c, i) => c.id !== current[i].id || c.title !== current[i].title);
    if (changed) {
      this.categories.set(categories);
    }
    const activeFilter = this.filterCategoryId();
    if (activeFilter !== null && !categories.some(c => c.id === activeFilter)) {
      this.filterCategoryId.set(null);
    }
  }

  protected toggleParticipants(): void {
    this.participantsOpen.update(v => !v);
  }

  protected toggleRoomClosed(): void {
    const currentRoomId = this.roomId();
    const current = this.room();
    if (!currentRoomId || !this.isRoomOwner() || !current) return;

    if (!current.closed) {
      const confirmed = window.confirm('This room will become read-only for everyone');
      if (!confirmed) return;
      this.roomsService.closeRoom(currentRoomId).subscribe({
        next: updatedRoom => this.roomChanged.emit(updatedRoom),
      });
    } else {
      this.roomsService.openRoom(currentRoomId).subscribe({
        next: updatedRoom => this.roomChanged.emit(updatedRoom),
      });
    }
  }

  protected deleteRoom(): void {
    const currentRoomId = this.roomId();
    if (!currentRoomId || !this.isRoomOwner()) return;

    const confirmed = window.confirm('Permanently delete this room? All ideas and votes will be lost.');
    if (!confirmed) return;

    this.roomsService.deleteRoom(currentRoomId).subscribe({
      next: () => void this.router.navigate(['/']),
    });
  }

  protected removeParticipant(member: User): void {
    const currentRoomId = this.roomId();
    if (!currentRoomId || !this.isRoomOwner()) return;

    const confirmed = window.confirm(`Remove ${member.username || 'this user'} from the room?`);
    if (!confirmed) return;

    this.roomsService.removeMember(currentRoomId, member.id).subscribe({
      next: updatedRoom => {
        this.roomChanged.emit(updatedRoom);
      },
      error: () => {
        this.roomsService.getRoom(currentRoomId).subscribe(refreshed => this.roomChanged.emit(refreshed));
      },
    });
  }

  protected readonly isRoomOwner = computed(() => {
    const currentUserId = this.userService.currentUser()?.id;
    const ownerId = this.room()?.owner?.id;
    return currentUserId != null && ownerId != null && currentUserId === ownerId;
  });

  protected startEditingRoomName(): void {
    if (!this.isRoomOwner() || this.room()?.closed) return;
    this.editingRoomName.set(this.room()?.name || '');
    this.roomNameError.set('');
    this.isEditingRoomName.set(true);
  }

  protected cancelEditingRoomName(): void {
    this.isEditingRoomName.set(false);
    this.roomNameError.set('');
  }

  protected saveRoomName(): void {
    if (!this.isRoomOwner() || this.room()?.closed) return;
    const name = this.editingRoomName().trim();
    if (!name) {
      this.roomNameError.set('Room name is required.');
      return;
    }
    if (name.length > 120) {
      this.roomNameError.set('Room name cannot exceed 120 characters.');
      return;
    }

    const currentRoomId = this.roomId();
    if (!currentRoomId) return;

    this.roomsService.updateRoom(currentRoomId, name).subscribe({
      next: updatedRoom => {
        this.roomChanged.emit(updatedRoom);
        this.isEditingRoomName.set(false);
        this.roomNameError.set('');
      },
      error: () => {
        this.roomNameError.set('Could not update room name.');
      },
    });
  }

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
      // reading the filters here restarts the sync immediately when a filter changes
      this.filters();
      if (currentRoomId) {
        this.loadLocalPositions(currentRoomId);
        this.startSync(currentRoomId);
      } else {
        this.stopSync();
      }
    });

    effect(() => {
      if (this.room()?.closed && this.selectedTool() === 'idea') {
        this.selectedTool.set('pen');
      }
    });
  }

  ngOnDestroy(): void {
    clearTimeout(this.searchTimer);
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
        exhaustMap(() =>
          forkJoin({
            ideas: this.mindspark.listIdeas(currentRoomId, this.filters()).pipe(
              timeout(10000),
              catchError(err => {
                console.error('Error fetching room ideas:', err);
                return of(null);
              })
            ),
            categories: this.categoriesService.listCategories(currentRoomId).pipe(
              timeout(10000),
              catchError(err => {
                console.error('Error fetching room categories:', err);
                return of(null);
              })
            ),
          })
        )
      )
      .subscribe(({ ideas, categories }) => {
        if (categories) {
          this.syncCategories(categories);
        }
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
        categoryId: idea.category?.id ?? null,
        categoryTitle: idea.category?.title ?? null,
        category: idea.category ?? null,
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
          ci.categoryId !== si.categoryId ||
          ci.categoryTitle !== si.categoryTitle ||
          ci.voteCount !== si.voteCount ||
          ci.voted !== si.voted
        );
      });

    if (isDifferent) {
      this.items.set([...nonIdeas, ...serverIdeas]);
    }
  }

  protected selectTool(tool: WorkspaceTool): void {
    if (tool === 'idea' && this.room()?.closed) return;
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
      if (this.room()?.closed) {
        this.finishPointer(surface);
        return;
      }
      // pre-select the active category filter so the new idea stays visible
      this.ideaDraft.set({ x: point.x, y: point.y, title: '', description: '', categoryId: this.filterCategoryId() });
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
    if (this.room()?.closed) return;
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
    if (this.room()?.closed || this.activeDragPointerId !== event.pointerId || !this.draggingIdea()) return;

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
              categoryId: currentItem.categoryId,
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
    if (this.room()?.closed) return;
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
    if (!draft || this.room()?.closed) return;
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
          categoryId: draft.categoryId,
          categoryTitle: this.categoryTitleOf(draft.categoryId),
          category: draft.categoryId == null ? null : { id: draft.categoryId, title: this.categoryTitleOf(draft.categoryId) ?? '' },
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
          categoryId: draft.categoryId,
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
              categoryId: newIdea.category?.id ?? null,
              categoryTitle: newIdea.category?.title ?? null,
              category: newIdea.category ?? null,
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
            item.kind === 'idea' && item.id === ideaId
              ? { ...item, title, description, categoryId: draft.categoryId, categoryTitle: this.categoryTitleOf(draft.categoryId) }
              : item
          )
        );
        this.ideaDraft.set(null);
        this.ideaError.set('');
        return;
      }

      this.mindspark.updateIdea(ideaId, { title, description, categoryId: draft.categoryId }).subscribe({
        next: updated => {
          this.commit(
            this.items().map(item =>
              item.kind === 'idea' && item.id === ideaId
                ? {
                    ...item,
                    title: updated.title || updated.text || title,
                    description: updated.description || description,
                    categoryId: updated.category?.id ?? null,
                    categoryTitle: updated.category?.title ?? null,
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
    if (this.room()?.closed || !this.isCreator(idea)) return;
    this.ideaDraft.set({
      id: idea.id,
      x: idea.x,
      y: idea.y,
      title: idea.title,
      description: idea.description,
      categoryId: idea.categoryId,
    });
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

  protected updateIdeaDraft(field: 'title' | 'description'| 'categoryId', value: string|number|null): void {
    this.ideaDraft.update(draft => (draft ? { ...draft, [field]: value } : draft));
  }

  protected updateIdeaDraftCategory(categoryId: number | null): void {
    this.ideaDraft.update(draft => (draft ? { ...draft, categoryId } : draft));
  }

  protected toggleVote(event: Event, idea: IdeaItem): void {
    event.stopPropagation();
    if (this.room()?.closed || !idea.id || !this.roomId()) return;

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
    if (this.room()?.closed) return;
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
