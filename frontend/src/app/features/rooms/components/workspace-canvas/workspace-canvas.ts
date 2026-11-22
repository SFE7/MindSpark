import { Component, HostListener, OnDestroy, ViewChild, computed, effect, inject, input, output, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { Subscription, forkJoin, of, timer } from 'rxjs';
import { catchError, exhaustMap, map, timeout } from 'rxjs/operators';
import { IdeaPositionGuard } from './idea-position-guard';
import { PresenceHelper } from './presence-helper';
import { DrawingsService } from '../../data-access/drawings.service';
import { DrawingResponse } from '../../models/drawing.model';
import { DrawingPositionGuard } from './drawing-position-guard';
import {
  CanvasPoint,
  BoundingBox,
  screenToleranceToCanvasUnits,
  hitTestStroke,
  hitTestShape,
  getStrokeBoundingBox,
  getShapeBoundingBox,
  simplifyPoints,
} from './drawing-hit-test';

import { CategoriesService } from '../../data-access/categories.service';
import { CommentsService } from '../../data-access/comments.service';
import { IdeasService } from '../../data-access/ideas.service';
import { RealtimeService } from '../../data-access/realtime.service';
import { RoomsService } from '../../data-access/rooms.service';
import { Category } from '../../models/category.model';
import { CommentItem } from '../../models/comment.model';
import { Idea } from '../../models/idea.model';
import { IdeaFilters, IdeaSortBy, Room } from '../../models/room.model';
import { KeycloakAuth } from '../../../../core/auth/keycloak-auth';
import { User } from '../../../../core/models/User';
import { UserService } from '../../../../core/user-service';
import { AiDrawerComponent } from '../ai-drawer/ai-drawer';
import { SessionHistoryService } from '../../../../core/sessions/session-history.service';
import { ExportFormat } from '../../../../core/sessions/session.models';
import { MediaService } from '../../../../core/media/media.service';
import { RoomMediaService } from '../../../../core/media/room-media.service';
import { CanvasImage, IdeaAttachment, RoomMediaBundle } from '../../../../core/media/media.model';
import { VotePoolService } from '../../../../core/votepool/vote-pool.service';
import { VotePool, VotePoolTally } from '../../../../core/votepool/vote-pool.model';

type WorkspaceTool = 'select' | 'pen' | 'highlighter' | 'shapes' | 'idea' | 'eraser';
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

export interface StrokeItem {
  kind: 'stroke';
  id: string;
  points: CanvasPoint[];
  color: string;
  width: number;
  opacity: number;
  offsetX?: number;
  offsetY?: number;
  authorId?: number;
  authorName?: string;
  version?: number;
  drawingKind?: 'pen' | 'highlighter';
}

export interface ShapeItem {
  kind: 'shape';
  id: string;
  shape: WorkspaceShape;
  x: number;
  y: number;
  width: number;
  height: number;
  color: string;
  offsetX?: number;
  offsetY?: number;
  authorId?: number;
  authorName?: string;
  version?: number;
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

export interface CanvasImageItem {
  kind: 'image';
  id: string;
  mediaId: string;
  x: number;
  y: number;
  width: number;
  height: number;
  naturalWidth?: number;
  naturalHeight?: number;
  zIndex: number;
  contentType?: string;
  uploadedById?: number;
  uploadedByName?: string;
  uploadedBySub?: string;
  createdAt?: string;
}

export interface RemoteCursor {
  userId: number | string;
  name: string;
  color: string;
  x: number;
  y: number;
  lastSeen: number;
}

type CanvasItem = StrokeItem | ShapeItem | IdeaItem | CanvasImageItem;

@Component({
  selector: 'app-workspace-canvas',
  standalone: true,
  imports: [FormsModule, RouterModule, AiDrawerComponent],
  templateUrl: './workspace-canvas.html',
  styleUrl: './workspace-canvas.scss',
})
export class WorkspaceCanvasComponent implements OnDestroy {
  readonly roomId = input<string>('');
  readonly room = input<Room | null>(null);
  readonly leave = output<void>();
  readonly roomChanged = output<Room>();

  protected readonly leftPanelCollapsed = signal<boolean>(false);
  protected readonly aiDrawerOpen = signal<boolean>(false);
  protected readonly aiSelectedIdea = signal<IdeaItem | null>(null);

  private readonly mindspark = inject(IdeasService);
  private readonly roomsService = inject(RoomsService);
  private readonly categoriesService = inject(CategoriesService);
  private readonly commentsService = inject(CommentsService);
  private readonly drawingsService = inject(DrawingsService);
  private readonly userService = inject(UserService);
  private readonly auth = inject(KeycloakAuth);
  private readonly router = inject(Router);
  protected readonly realtime = inject(RealtimeService);
  protected readonly sessionHistory = inject(SessionHistoryService);
  protected readonly votePoolService = inject(VotePoolService);
  protected readonly mediaService = inject(MediaService);
  protected readonly roomMediaService = inject(RoomMediaService);
  @ViewChild(AiDrawerComponent) protected readonly aiDrawerComponent?: AiDrawerComponent;

  // Vote Pool signals (Story 37)
  protected readonly activeVotePool = this.votePoolService.activePool;
  protected readonly latestClosedVotePool = this.votePoolService.latestClosedPool;
  protected readonly myVotesRemaining = this.votePoolService.myVotesRemaining;
  protected readonly myVotesUsed = this.votePoolService.myVotesUsed;
  protected readonly showStartVotePoolModal = signal<boolean>(false);
  protected readonly votePoolTitle = signal<string>('');
  protected readonly votePoolVotesPerParticipant = signal<number>(3);
  protected readonly votePoolAllowMultiple = signal<boolean>(true);
  protected readonly votePoolSubmitting = signal<boolean>(false);
  protected readonly votePoolModalError = signal<string>('');
  protected readonly showVotePoolResultsModal = signal<boolean>(false);

  // Media signals (Story 26)
  protected readonly ideaAttachments = signal<Map<string, IdeaAttachment[]>>(new Map());
  protected readonly targetIdeaForAttach = signal<IdeaItem | null>(null);
  protected readonly lightboxAttachment = signal<IdeaAttachment | null>(null);
  protected readonly lightboxCanDelete = signal<boolean>(false);
  protected readonly isUploadingMedia = signal<boolean>(false);

  protected readonly canvasImages = signal<CanvasImageItem[]>([]);
  protected readonly selectedCanvasImageId = signal<string | null>(null);

  private activeCanvasImageDragPointerId: number | null = null;
  private canvasImageDragStartPointer = { x: 0, y: 0 };
  private canvasImageDragStartPos = { x: 0, y: 0 };
  private canvasImageHasDragged = false;

  private activeCanvasImageResizePointerId: number | null = null;
  private canvasImageResizeStartPointer = { x: 0, y: 0 };
  private canvasImageResizeStartSize = { width: 0, height: 0 };
  private canvasImageResizeItem: CanvasImageItem | null = null;

  private votePoolSub?: Subscription;
  private ideaAttachmentSub?: Subscription;
  private canvasImageSub?: Subscription;

  protected readonly saveExportMenuOpen = signal<boolean>(false);
  protected readonly saveModalOpen = signal<boolean>(false);
  protected readonly saveSnapshotTitle = signal<string>('');
  protected readonly saveSnapshotNotes = signal<string>('');
  protected readonly includeAiSummary = signal<boolean>(false);
  protected readonly availableAiSummary = signal<string | null>(null);
  protected readonly savedToast = signal<{ id: string; title: string } | null>(null);
  protected readonly isSavingSnapshot = signal<boolean>(false);

  protected readonly onlineParticipants = this.realtime.onlineParticipants;
  protected readonly connectionStatus = this.realtime.connectionStatus;
  protected readonly remoteCursors = signal<Map<string | number, RemoteCursor>>(new Map());
  protected readonly remoteCursorList = computed(() => Array.from(this.remoteCursors().values()));
  private cardMovesSub?: Subscription;
  private cursorMovesSub?: Subscription;
  private cursorLeavesSub?: Subscription;
  private cursorPruneTimer?: ReturnType<typeof setInterval>;
  private ideaEventsSub?: Subscription;
  private commentEventsSub?: Subscription;
  private drawingEventsSub?: Subscription;
  private drawingMovesSub?: Subscription;
  private strokePointsSub?: Subscription;
  private objectLockedSub?: Subscription;
  private objectUnlockedSub?: Subscription;
  private categoryEventsSub?: Subscription;

  private readonly ideaVersions = new Map<string, number>();
  private readonly recentlyDeletedIds = new Map<string, number>();
  private readonly drawingPositionGuard = new DrawingPositionGuard();
  private readonly drawingVersions = new Map<string, number>();
  private readonly recentlyDeletedDrawingIds = new Map<string, number>();

  private isRecentlyDeleted(id: string): boolean {
    const deletedAt = this.recentlyDeletedIds.get(id);
    if (!deletedAt) return false;
    if (Date.now() - deletedAt > 60000) {
      this.recentlyDeletedIds.delete(id);
      return false;
    }
    return true;
  }

  private markDeleted(id: string): void {
    this.recentlyDeletedIds.set(id, Date.now());
  }

  private isRecentlyDeletedDrawing(id: string): boolean {
    const deletedAt = this.recentlyDeletedDrawingIds.get(id);
    if (!deletedAt) return false;
    if (Date.now() - deletedAt > 60000) {
      this.recentlyDeletedDrawingIds.delete(id);
      return false;
    }
    return true;
  }

  private markDrawingDeleted(id: string): void {
    this.recentlyDeletedDrawingIds.set(id, Date.now());
  }

  protected readonly transientError = signal<string>('');
  private transientErrorTimer?: ReturnType<typeof setTimeout>;

  protected showTransientError(message: string): void {
    this.transientError.set(message);
    clearTimeout(this.transientErrorTimer);
    this.transientErrorTimer = setTimeout(() => {
      this.transientError.set('');
    }, 3500);
  }

  // Soft locking
  protected readonly heldObjects = signal<Map<string, { objectType: 'card' | 'drawing'; objectId: string; userId: number | string; userName: string; expiresAt: number }>>(new Map());

  protected isItemLocked(objectType: 'card' | 'drawing', objectId: string): boolean {
    const key = `${objectType}:${objectId}`;
    const held = this.heldObjects().get(key);
    if (!held) return false;
    if (Date.now() > held.expiresAt) return false;
    const myId = this.userService.currentUser()?.id;
    return myId == null || String(held.userId) !== String(myId);
  }

  protected getItemHolderName(objectType: 'card' | 'drawing', objectId: string): string | null {
    const key = `${objectType}:${objectId}`;
    const held = this.heldObjects().get(key);
    if (!held) return null;
    if (Date.now() > held.expiresAt) return null;
    return held.userName;
  }

  // Remote stroke previews
  protected readonly remotePreviews = signal<Map<string, { strokeId: string; points: CanvasPoint[]; color: string; width: number; opacity: number; lastUpdated: number }>>(new Map());
  protected readonly remotePreviewList = computed(() => Array.from(this.remotePreviews().values()));

  // Selected drawing
  protected readonly selectedDrawingId = signal<string | null>(null);
  protected readonly selectedDrawing = computed(() => {
    const id = this.selectedDrawingId();
    if (!id) return null;
    return (this.items().find(i => (i.kind === 'stroke' || i.kind === 'shape') && i.id === id) as StrokeItem | ShapeItem | undefined) ?? null;
  });
  protected readonly selectionBox = computed<{ x: number; y: number; width: number; height: number } | null>(() => {
    const item = this.selectedDrawing();
    if (!item) return null;
    let box: BoundingBox;
    if (item.kind === 'stroke') {
      box = getStrokeBoundingBox(item.points, item.offsetX || 0, item.offsetY || 0);
    } else if (item.kind === 'shape') {
      box = getShapeBoundingBox(item);
    } else {
      return null;
    }
    return {
      x: box.minX,
      y: box.minY,
      width: Math.max(box.maxX - box.minX, 4),
      height: Math.max(box.maxY - box.minY, 4),
    };
  });

  protected readonly isOffline = computed(() => !this.room()?.closed && Boolean(this.room()?.offline));
  protected readonly isRoomLocked = computed(() => Boolean(this.room()?.closed || this.room()?.offline));

  protected goHome(): void {
    void this.router.navigate(['/']);
  }

  protected onLeave(): void {
    this.stopPresence();
    this.realtime.leaveRoom();
    this.leave.emit();
  }

  protected readonly tools: ToolOption[] = [
    { id: 'select', label: 'Select', iconPath: '/assets/mindspark/select.png' },
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

  // ---------- Comments (Phase 3 - stories #32-34) ----------
  protected readonly commentsOpen = signal(false);
  protected readonly activeCommentIdea = signal<IdeaItem | null>(null);
  protected readonly comments = signal<CommentItem[]>([]);
  protected readonly commentsLoading = signal(false);
  protected readonly commentError = signal('');
  protected readonly newCommentContent = signal('');
  protected readonly replyingToCommentId = signal<string | null>(null);
  protected readonly replyContent = signal('');
  protected readonly editingCommentId = signal<string | null>(null);
  protected readonly editingCommentContent = signal('');

  private readonly filters = computed<IdeaFilters>(() => ({
    categoryId: this.filterCategoryId(),
    search: this.searchTerm(),
    sortBy: this.sortBy(),
  }));
  protected readonly hasActiveFilters = computed(
    () => this.filterCategoryId() !== null || this.searchTerm() !== ''
  );
  protected readonly canManageCategories = computed(() => this.isRoomOwner() && !this.isRoomLocked());

  protected onSearchInput(value: string): void {
    this.searchInput.set(value);
    clearTimeout(this.searchTimer);
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

  protected removeParticipant(member: User): void {
    const currentRoomId = this.roomId();
    if (!currentRoomId || !this.isRoomOwner() || this.isRoomLocked()) return;

    const confirmed = window.confirm(`Remove ${member.username || 'this user'} from the room?`);
    if (!confirmed) return;

    this.roomsService.removeMember(currentRoomId, member.id).subscribe({
      next: updatedRoom => {
        this.roomChanged.emit(updatedRoom);
      },
      error: err => {
        console.error('Failed to remove participant', err);
      },
    });
  }

  protected removeParticipantById(userId: number | string, name: string): void {
    const currentRoomId = this.roomId();
    if (!currentRoomId || !this.isRoomOwner() || this.isRoomLocked()) return;

    const confirmed = window.confirm(`Remove ${name || 'this user'} from the room?`);
    if (!confirmed) return;

    this.roomsService.removeMember(currentRoomId, Number(userId)).subscribe({
      next: updatedRoom => {
        this.roomChanged.emit(updatedRoom);
      },
      error: err => {
        console.error('Failed to remove participant', err);
      },
    });
  }

  protected startEditingRoomName(): void {
    if (!this.isRoomOwner() || this.isRoomLocked()) return;
    this.editingRoomName.set(this.room()?.name || '');
    this.roomNameError.set('');
    this.isEditingRoomName.set(true);
  }

  protected cancelEditingRoomName(): void {
    this.isEditingRoomName.set(false);
    this.roomNameError.set('');
  }

  protected saveRoomName(): void {
    const currentRoomId = this.roomId();
    if (!currentRoomId || !this.isRoomOwner() || this.isRoomLocked()) return;

    const trimmed = this.editingRoomName().trim();
    if (!trimmed) {
      this.roomNameError.set('Room name is required.');
      return;
    }

    this.roomsService.updateRoom(currentRoomId, trimmed).subscribe({
      next: updatedRoom => {
        this.roomChanged.emit(updatedRoom);
        this.isEditingRoomName.set(false);
        this.roomNameError.set('');
      },
      error: () => this.roomNameError.set('Could not rename room.'),
    });
  }

  protected isRoomOwner(): boolean {
    const current = this.room();
    const currentUserId = this.userService.currentUser()?.id;
    return Boolean(current && currentUserId !== undefined && current.owner?.id === currentUserId);
  }

  protected openComments(event: Event, idea: IdeaItem): void {
    event.stopPropagation();
    this.activeCommentIdea.set(idea);
    this.commentsOpen.set(true);
    this.commentError.set('');
    this.newCommentContent.set('');
    this.replyingToCommentId.set(null);
    this.replyContent.set('');
    this.editingCommentId.set(null);
    this.editingCommentContent.set('');
    this.loadComments(idea.id);
  }

  protected closeComments(): void {
    this.commentsOpen.set(false);
    this.activeCommentIdea.set(null);
    this.comments.set([]);
    this.commentError.set('');
    this.replyingToCommentId.set(null);
    this.editingCommentId.set(null);
  }

  private loadComments(ideaId: string): void {
    this.commentsLoading.set(true);
    this.commentsService.getComments(ideaId).subscribe({
      next: data => {
        this.comments.set(data);
        this.commentsLoading.set(false);
      },
      error: () => {
        this.commentError.set('Failed to load comments.');
        this.commentsLoading.set(false);
      },
    });
  }

  protected addComment(): void {
    const idea = this.activeCommentIdea();
    if (!idea || this.isRoomLocked()) return;
    const content = this.newCommentContent().trim();
    if (!content) return;

    this.commentsService.addComment(idea.id, content).subscribe({
      next: comment => {
        this.comments.update(list => [...list, comment]);
        this.newCommentContent.set('');
        this.commentError.set('');
      },
      error: () => this.commentError.set('Failed to post comment.'),
    });
  }

  protected startReply(commentId: string): void {
    if (this.isRoomLocked()) return;
    this.replyingToCommentId.set(commentId);
    this.replyContent.set('');
  }

  protected cancelReply(): void {
    this.replyingToCommentId.set(null);
    this.replyContent.set('');
  }

  protected sendReply(parentId: string): void {
    const idea = this.activeCommentIdea();
    if (!idea || this.isRoomLocked()) return;
    const content = this.replyContent().trim();
    if (!content) return;

    this.commentsService.replyToComment(parentId, content).subscribe({
      next: reply => {
        this.comments.update(list =>
          list.map(c => {
            if (c.id === parentId) {
              return { ...c, replies: [...(c.replies || []), reply] };
            }
            return c;
          })
        );
        this.replyingToCommentId.set(null);
        this.replyContent.set('');
        this.commentError.set('');
      },
      error: () => this.commentError.set('Failed to post reply.'),
    });
  }

  protected startEditComment(comment: CommentItem): void {
    if (this.isRoomLocked()) return;
    this.editingCommentId.set(comment.id);
    this.editingCommentContent.set(comment.content || comment.text || '');
  }

  protected cancelEditComment(): void {
    this.editingCommentId.set(null);
    this.editingCommentContent.set('');
  }

  protected saveEditComment(commentId: string): void {
    if (this.isRoomLocked()) return;
    const content = this.editingCommentContent().trim();
    if (!content) return;

    this.commentsService.updateComment(commentId, content).subscribe({
      next: updated => {
        this.comments.update(list =>
          list.map(c => {
            if (c.id === commentId) {
              return { ...c, content: updated.content, text: updated.content, updatedAt: updated.updatedAt };
            }
            if (c.replies) {
              return {
                ...c,
                replies: c.replies.map(r =>
                  r.id === commentId
                    ? { ...r, content: updated.content, text: updated.content, updatedAt: updated.updatedAt }
                    : r
                ),
              };
            }
            return c;
          })
        );
        this.editingCommentId.set(null);
        this.editingCommentContent.set('');
        this.commentError.set('');
      },
      error: () => this.commentError.set('Failed to update comment.'),
    });
  }

  protected deleteComment(comment: CommentItem): void {
    if (this.isRoomLocked()) return;
    this.commentsService.deleteComment(comment.id).subscribe({
      next: () => {
        this.comments.update(list => {
          if (comment.parentId) {
            return list.map(c => {
              if (c.id === comment.parentId) {
                return { ...c, replies: (c.replies || []).filter(r => r.id !== comment.id) };
              }
              return c;
            });
          }
          return list.filter(c => c.id !== comment.id);
        });
        this.commentError.set('');
      },
      error: () => this.commentError.set('Failed to delete comment.'),
    });
  }

  protected isCommentAuthor(comment: CommentItem): boolean {
    const currentUserId = this.userService.currentUser()?.id;
    return currentUserId != null && comment.author?.id === currentUserId;
  }

  private nextItemId = 1;
  private history: CanvasItem[][] = [[]];
  private readonly historyIndex = signal(0);
  private pointerId: number | null = null;
  private startPoint: CanvasPoint | null = null;
  private draftPoints: CanvasPoint[] = [];
  private actionStartItems: CanvasItem[] | null = null;
  private pollSub?: Subscription;

  private currentStrokeId = '';
  private lastStrokePointsBroadcastTime = 0;
  private draggingDrawing: {
    id: string;
    startPointer: CanvasPoint;
    startOffsetX: number;
    startOffsetY: number;
    currentOffsetX: number;
    currentOffsetY: number;
    hasMoved: boolean;
  } | null = null;
  private lastDrawingMoveBroadcastTime = 0;

  private activeDragPointerId: number | null = null;
  private dragStartPointer = { x: 0, y: 0 };
  private dragStartIdeaPos = { x: 0, y: 0 };
  private hasDragged = false;
  private localPositions = new Map<string, { x: number; y: number }>();
  private readonly positionGuard = new IdeaPositionGuard();
  private ideaErrorTimer?: number;
  private presenceHelper?: PresenceHelper;

  constructor() {
    this.realtime.reconnected$.subscribe(() => {
      const currentRoomId = this.roomId();
      if (currentRoomId) {
        this.startSync(currentRoomId);
      }
    });

    this.cardMovesSub = this.realtime.cardMoves$.subscribe(move => {
      if (this.draggingIdea()?.id === move.id) {
        return;
      }

      this.positionGuard.registerRemoteMove(move.id, move.x, move.y);
      this.localPositions.set(move.id, { x: move.x, y: move.y });
      this.saveLocalPositions();

      this.items.update(items =>
        items.map(item =>
          item.kind === 'idea' && item.id === move.id
            ? { ...item, x: move.x, y: move.y }
            : item
        )
      );
    });

    this.cursorMovesSub = this.realtime.cursorMoves$.subscribe(move => {
      const myId = this.userService.currentUser()?.id;
      if (myId != null && String(move.userId) === String(myId)) {
        return;
      }
      this.remoteCursors.update(map => {
        const next = new Map(map);
        next.set(move.userId, {
          userId: move.userId,
          name: move.name,
          color: move.color,
          x: move.x,
          y: move.y,
          lastSeen: Date.now(),
        });
        return next;
      });
    });

    this.cursorLeavesSub = this.realtime.cursorLeaves$.subscribe(leave => {
      this.remoteCursors.update(map => {
        if (!map.has(leave.userId)) return map;
        const next = new Map(map);
        next.delete(leave.userId);
        return next;
      });
    });

    this.cursorPruneTimer = setInterval(() => {
      const now = Date.now();
      let changed = false;
      const current = this.remoteCursors();
      for (const [, c] of current.entries()) {
        if (now - c.lastSeen > 3000) {
          changed = true;
          break;
        }
      }
      if (changed) {
        this.remoteCursors.update(map => {
          const next = new Map();
          for (const [uid, c] of map.entries()) {
            if (now - c.lastSeen <= 3000) {
              next.set(uid, c);
            }
          }
          return next;
        });
      }

      // Prune expired soft locks
      let locksChanged = false;
      for (const [, h] of this.heldObjects().entries()) {
        if (now > h.expiresAt) {
          locksChanged = true;
          break;
        }
      }
      if (locksChanged) {
        this.heldObjects.update(map => {
          const next = new Map();
          for (const [k, h] of map.entries()) {
            if (now <= h.expiresAt) next.set(k, h);
          }
          return next;
        });
      }

      // Prune stale remote previews (>5000ms)
      let previewsChanged = false;
      for (const [, p] of this.remotePreviews().entries()) {
        if (now - p.lastUpdated > 5000) {
          previewsChanged = true;
          break;
        }
      }
      if (previewsChanged) {
        this.remotePreviews.update(map => {
          const next = new Map();
          for (const [k, p] of map.entries()) {
            if (now - p.lastUpdated <= 5000) next.set(k, p);
          }
          return next;
        });
      }
    }, 1000);

    this.ideaEventsSub = this.realtime.ideaEvents$.subscribe(event => {
      if (event.type === 'idea-deleted' && event.id) {
        this.markDeleted(event.id);
        this.items.update(items => items.filter(item => item.id !== event.id));
        this.localPositions.delete(event.id);
        this.saveLocalPositions();
        if (this.activeCommentIdea()?.id === event.id) {
          this.closeComments();
        }
        return;
      }

      if ((event.type === 'idea-created' || event.type === 'idea-updated') && event.idea) {
        const idea = event.idea;
        if (this.isRecentlyDeleted(idea.id)) {
          return;
        }

        const serverVersion = event.version || (idea.updatedAt ? new Date(idea.updatedAt).getTime() : 0);
        const currentVersion = this.ideaVersions.get(idea.id) || 0;
        if (serverVersion < currentVersion) {
          return;
        }
        this.ideaVersions.set(idea.id, serverVersion);

        const currentItems = this.items();
        const existingIndex = currentItems.findIndex(i => i.id === idea.id);
        const currentlyDragging = this.draggingIdea();
        const isBeingDragged = currentlyDragging !== null && currentlyDragging.id === idea.id;
        const pending = this.positionGuard.getPendingMove(idea.id);

        let x: number;
        let y: number;

        if (existingIndex >= 0) {
          const existing = currentItems[existingIndex] as IdeaItem;
          if (isBeingDragged) {
            x = existing.x;
            y = existing.y;
          } else if (pending) {
            x = pending.x;
            y = pending.y;
          } else {
            x = idea.x != null ? idea.x : existing.x;
            y = idea.y != null ? idea.y : existing.y;
            this.localPositions.set(idea.id, { x, y });
            this.saveLocalPositions();
          }
        } else {
          x = idea.x != null ? idea.x : 500;
          y = idea.y != null ? idea.y : 250;
          this.localPositions.set(idea.id, { x, y });
          this.saveLocalPositions();
        }

        const updatedIdeaItem: IdeaItem = {
          kind: 'idea',
          id: idea.id,
          x,
          y,
          color: idea.color || '#9fc9a9',
          creatorId: idea.author?.id ?? null,
          title: idea.title || idea.text || 'Untitled Idea',
          description: idea.description || '',
          categoryId: idea.category?.id ?? null,
          categoryTitle: idea.category?.title ?? null,
          category: idea.category ?? null,
          voteCount: idea.voteCount ?? 0,
          voted: !!idea.voted,
        };

        if (existingIndex >= 0) {
          this.items.update(items =>
            items.map(i => i.id === idea.id ? updatedIdeaItem : i)
          );
        } else {
          this.items.update(items => [...items, updatedIdeaItem]);
        }
      }
    });

    this.commentEventsSub = this.realtime.commentEvents$.subscribe(event => {
      const activeIdea = this.activeCommentIdea();
      if (!activeIdea || activeIdea.id !== event.ideaId) {
        return;
      }

      if (event.type === 'comment-deleted' && event.commentId) {
        this.comments.update(list =>
          list
            .filter(c => c.id !== event.commentId)
            .map(c => ({
              ...c,
              replies: (c.replies || []).filter(r => r.id !== event.commentId),
            }))
        );
      } else if (event.type === 'comment-created' && event.comment) {
        const newComment = event.comment;
        this.comments.update(list => {
          if (newComment.parentId) {
            return list.map(c => {
              if (c.id === newComment.parentId) {
                const existingReplies = c.replies || [];
                if (existingReplies.some(r => r.id === newComment.id)) {
                  return {
                    ...c,
                    replies: existingReplies.map(r => r.id === newComment.id ? newComment : r),
                  };
                }
                return { ...c, replies: [...existingReplies, newComment] };
              }
              return c;
            });
          } else {
            if (list.some(c => c.id === newComment.id)) {
              return list.map(c => c.id === newComment.id ? newComment : c);
            }
            return [...list, newComment];
          }
        });
      } else if (event.type === 'comment-updated' && event.comment) {
        const updatedComment = event.comment;
        this.comments.update(list =>
          list.map(c => {
            if (c.id === updatedComment.id) {
              return { ...c, content: updatedComment.content, text: updatedComment.content, updatedAt: updatedComment.updatedAt };
            }
            if (c.replies && c.replies.some(r => r.id === updatedComment.id)) {
              return {
                ...c,
                replies: c.replies.map(r =>
                  r.id === updatedComment.id
                    ? { ...r, content: updatedComment.content, text: updatedComment.content, updatedAt: updatedComment.updatedAt }
                    : r
                ),
              };
            }
            return c;
          })
        );
      }
    });

    this.drawingEventsSub = this.realtime.drawingEvents$.subscribe(event => {
      if (event.type === 'drawing-deleted' && event.id) {
        const delId = event.id;
        this.markDrawingDeleted(delId);
        this.items.update(items => items.filter(i => i.id !== delId));
        if (this.selectedDrawingId() === delId) {
          this.selectedDrawingId.set(null);
        }
        this.remotePreviews.update(map => {
          if (!map.has(delId)) return map;
          const next = new Map(map);
          next.delete(delId);
          return next;
        });
        return;
      }

      if ((event.type === 'drawing-created' || event.type === 'drawing-updated') && event.drawing) {
        const d = event.drawing;
        if (this.isRecentlyDeletedDrawing(d.id)) return;

        const serverVersion = event.version || (d.updatedAt ? new Date(d.updatedAt).getTime() : 0);
        const curVersion = this.drawingVersions.get(d.id) || 0;
        if (serverVersion < curVersion) return;
        this.drawingVersions.set(d.id, serverVersion);

        this.remotePreviews.update(map => {
          if (!map.has(d.id)) return map;
          const next = new Map(map);
          next.delete(d.id);
          return next;
        });

        const parsedItem = this.mapDrawingResponseToCanvasItem(d);
        if (!parsedItem) return;

        if (this.draggingDrawing?.id === d.id) return;

        const pending = this.drawingPositionGuard.getPendingMove(d.id);
        if (pending) {
          parsedItem.offsetX = pending.offsetX;
          parsedItem.offsetY = pending.offsetY;
        }

        this.items.update(items => {
          const idx = items.findIndex(i => i.id === d.id);
          if (idx >= 0) {
            return items.map(i => i.id === d.id ? parsedItem : i);
          } else {
            return [...items, parsedItem];
          }
        });
      }
    });

    this.drawingMovesSub = this.realtime.drawingMoves$.subscribe(move => {
      if (this.draggingDrawing?.id === move.id) return;

      this.drawingPositionGuard.registerRemoteMove(move.id, move.offsetX, move.offsetY);
      this.items.update(items =>
        items.map(item => {
          if ((item.kind === 'stroke' || item.kind === 'shape') && item.id === move.id) {
            return { ...item, offsetX: move.offsetX, offsetY: move.offsetY };
          }
          return item;
        })
      );
    });

    this.strokePointsSub = this.realtime.strokePoints$.subscribe(sp => {
      const myId = this.userService.currentUser()?.id;
      if (myId != null && String(sp.userId) === String(myId)) return;

      this.remotePreviews.update(map => {
        const next = new Map(map);
        next.set(sp.strokeId, {
          strokeId: sp.strokeId,
          points: sp.points,
          color: sp.color,
          width: sp.width,
          opacity: sp.opacity,
          lastUpdated: Date.now(),
        });
        return next;
      });
    });

    this.objectLockedSub = this.realtime.objectLocked$.subscribe(lock => {
      this.heldObjects.update(map => {
        const next = new Map(map);
        next.set(`${lock.objectType}:${lock.id}`, {
          objectType: lock.objectType,
          objectId: lock.id,
          userId: lock.userId,
          userName: lock.name,
          expiresAt: Date.now() + 3000,
        });
        return next;
      });
    });

    this.objectUnlockedSub = this.realtime.objectUnlocked$.subscribe(unlock => {
      this.heldObjects.update(map => {
        const next = new Map(map);
        next.delete(`${unlock.objectType}:${unlock.id}`);
        return next;
      });
    });

    this.categoryEventsSub = this.realtime.categoryEvents$.subscribe(event => {
      const currentRoomId = this.roomId();
      if (currentRoomId) {
        this.categoriesService.listCategories(currentRoomId).subscribe({
          next: categories => this.syncCategories(categories),
          error: err => console.error('Error refreshing room categories:', err),
        });
      }

      if (event.type === 'category-updated' && event.category) {
        const updatedCat = event.category;
        this.items.update(items =>
          items.map(item =>
            item.kind === 'idea' && item.categoryId === updatedCat.id
              ? { ...item, categoryTitle: updatedCat.title, category: { id: updatedCat.id, title: updatedCat.title } }
              : item
          )
        );
      } else if (event.type === 'category-deleted' && event.id != null) {
        const deletedId = Number(event.id);
        this.items.update(items =>
          items.map(item =>
            item.kind === 'idea' && item.categoryId === deletedId
              ? { ...item, categoryId: null, categoryTitle: null, category: null }
              : item
          )
        );
      }
    });

    this.votePoolSub = this.realtime.votePoolEvents$.subscribe(pool => {
      if (pool) {
        if (pool.status === 'OPEN') {
          this.votePoolService.activePool.set(pool);
        } else if (pool.status === 'CLOSED') {
          this.votePoolService.activePool.set(null);
          this.votePoolService.latestClosedPool.set(pool);
        }
      }
    });

    this.ideaAttachmentSub = this.realtime.ideaAttachmentEvents$.subscribe(evt => {
      if (!evt || !evt.attachment) return;
      const att = evt.attachment;
      this.ideaAttachments.update(currentMap => {
        const nextMap = new Map(currentMap);
        const list = nextMap.get(att.ideaId) ? [...nextMap.get(att.ideaId)!] : [];
        if (evt.type === 'CREATED') {
          if (!list.some(a => a.id === att.id)) {
            list.push(att);
            this.mediaService.fetchImageBlobUrl(this.roomId(), att.mediaId).subscribe();
          }
        } else if (evt.type === 'DELETED') {
          const idx = list.findIndex(a => a.id === att.id);
          if (idx !== -1) list.splice(idx, 1);
        }
        nextMap.set(att.ideaId, list);
        return nextMap;
      });
    });

    this.canvasImageSub = this.realtime.canvasImageEvents$.subscribe(evt => {
      if (!evt || !evt.canvasImage) return;
      const ci = evt.canvasImage;
      if (evt.type === 'CREATED') {
        this.canvasImages.update(list => {
          if (list.some(i => i.id === ci.id)) return list;
          this.mediaService.fetchImageBlobUrl(this.roomId(), ci.mediaId).subscribe();
          return [...list, {
            kind: 'image',
            id: ci.id,
            mediaId: ci.mediaId,
            x: ci.x,
            y: ci.y,
            width: ci.width,
            height: ci.height,
            naturalWidth: ci.naturalWidth,
            naturalHeight: ci.naturalHeight,
            zIndex: ci.zIndex,
            contentType: ci.contentType,
            uploadedById: ci.uploadedById,
            uploadedByName: ci.uploadedByName,
            uploadedBySub: ci.uploadedBySub,
            createdAt: ci.createdAt
          }];
        });
      } else if (evt.type === 'UPDATED') {
        this.canvasImages.update(list =>
          list.map(item => item.id === ci.id ? {
            ...item,
            x: ci.x,
            y: ci.y,
            width: ci.width,
            height: ci.height,
            zIndex: ci.zIndex
          } : item)
        );
      } else if (evt.type === 'DELETED') {
        this.canvasImages.update(list => list.filter(item => item.id !== ci.id));
        if (this.selectedCanvasImageId() === ci.id) {
          this.selectedCanvasImageId.set(null);
        }
      }
    });

    effect(() => {
      const participants = this.onlineParticipants();
      const participantIds = new Set(participants.map(p => String(p.userId)));
      this.remoteCursors.update(map => {
        let changed = false;
        for (const uid of map.keys()) {
          if (!participantIds.has(String(uid))) {
            changed = true;
            break;
          }
        }
        if (!changed) return map;
        const next = new Map();
        for (const [uid, c] of map.entries()) {
          if (participantIds.has(String(uid))) {
            next.set(uid, c);
          }
        }
        return next;
      });
    });

    effect(() => {
      const currentRoomId = this.roomId();
      this.filters();
      if (currentRoomId) {
        this.positionGuard.clear();
        this.drawingPositionGuard.clear();
        this.loadLocalPositions(currentRoomId);
        this.startSync(currentRoomId);
        this.initPresence(currentRoomId);
        const user = this.userService.currentUser();
        this.realtime.joinRoom(currentRoomId, {
          userId: user?.id ?? 0,
          name: user?.username || 'Participant',
        });
      } else {
        this.stopSync();
        this.stopPresence();
        this.realtime.leaveRoom();
        this.positionGuard.clear();
        this.drawingPositionGuard.clear();
      }
    });

    effect(() => {
      if (this.isRoomLocked() && (this.selectedTool() === 'idea' || this.selectedTool() === 'pen')) {
        this.selectedTool.set('select');
      }
    });
  }

  ngOnDestroy(): void {
    clearTimeout(this.searchTimer);
    clearTimeout(this.ideaErrorTimer);
    clearTimeout(this.transientErrorTimer);
    if (this.cursorPruneTimer) {
      clearInterval(this.cursorPruneTimer);
    }
    this.stopSync();
    this.stopPresence();
    this.cardMovesSub?.unsubscribe();
    this.cursorMovesSub?.unsubscribe();
    this.cursorLeavesSub?.unsubscribe();
    this.ideaEventsSub?.unsubscribe();
    this.commentEventsSub?.unsubscribe();
    this.drawingEventsSub?.unsubscribe();
    this.drawingMovesSub?.unsubscribe();
    this.strokePointsSub?.unsubscribe();
    this.objectLockedSub?.unsubscribe();
    this.objectUnlockedSub?.unsubscribe();
    this.categoryEventsSub?.unsubscribe();
    this.votePoolSub?.unsubscribe();
    this.ideaAttachmentSub?.unsubscribe();
    this.canvasImageSub?.unsubscribe();
    this.votePoolService.reset();
    this.mediaService.revokeAll();
    this.remoteCursors.set(new Map());
    this.realtime.leaveRoom();
    this.positionGuard.clear();
    this.drawingPositionGuard.clear();
  }

  private initPresence(roomId: string): void {
    this.stopPresence();
    this.presenceHelper = new PresenceHelper({
      roomId,
      roomsService: this.roomsService,
      auth: this.auth,
      onUserRemoved: () => this.handleUserRemoved(),
    });
  }

  private stopPresence(): void {
    if (this.presenceHelper) {
      this.presenceHelper.destroy();
      this.presenceHelper = undefined;
    }
  }

  private handleUserRemoved(): void {
    this.stopSync();
    this.stopPresence();
    this.cardMovesSub?.unsubscribe();
    this.realtime.leaveRoom();
    void this.router.navigate(['/'], {
      queryParams: { error: 'You were removed from this room' },
      state: { message: 'You were removed from this room' },
    });
  }

  private loadLocalPositions(roomId: string): void {
    try {
      const saved = localStorage.getItem(`mindspark-pos-${roomId}`);
      if (saved) {
        const parsed = JSON.parse(saved) as Record<string, { x: number; y: number }>;
        this.localPositions = new Map(Object.entries(parsed));
        for (const [id, pos] of this.localPositions.entries()) {
          this.positionGuard.trackConfirmedPosition(id, pos.x, pos.y);
        }
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
    this.pollSub = timer(0, 15000)
      .pipe(
        exhaustMap(() => {
          const pollGen = this.positionGuard.nextPollGeneration();
          return forkJoin({
            room: this.roomsService.getRoom(currentRoomId).pipe(
              timeout(10000),
              catchError((err: HttpErrorResponse) => {
                if (err.status === 403) {
                  this.handleUserRemoved();
                }
                return of(null);
              })
            ),
            ideas: this.mindspark.listIdeas(currentRoomId, this.filters()).pipe(
              timeout(10000),
              catchError(err => {
                if (err && typeof err === 'object' && 'status' in err && (err as { status: number }).status === 403) {
                  this.handleUserRemoved();
                }
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
            drawings: this.drawingsService.getRoomDrawings(currentRoomId).pipe(
              timeout(10000),
              catchError(err => {
                console.error('Error fetching room drawings:', err);
                return of(null);
              })
            ),
            attachments: this.roomMediaService.getRoomAttachments(currentRoomId).pipe(
              timeout(10000),
              catchError(err => {
                console.error('Error fetching room attachments:', err);
                return of(null);
              })
            ),
            votePool: this.votePoolService.loadPool(currentRoomId, true).pipe(
              timeout(10000),
              catchError(err => {
                console.error('Error fetching vote pool:', err);
                return of(null);
              })
            ),
          }).pipe(
            map(result => ({ ...result, pollGen }))
          );
        })
      )
      .subscribe(({ room, ideas, categories, drawings, attachments, pollGen }) => {
        if (room) {
          this.roomChanged.emit(room);
        }
        if (categories) {
          this.syncCategories(categories);
        }
        if (ideas) {
          this.syncServerIdeas(ideas, pollGen);
        }
        if (drawings) {
          this.syncServerDrawings(drawings, pollGen);
        }
        if (attachments) {
          this.syncRoomMediaBundle(attachments);
        }
      });
  }

  private stopSync(): void {
    if (this.pollSub) {
      this.pollSub.unsubscribe();
      this.pollSub = undefined;
    }
  }

  private syncServerIdeas(ideas: Idea[], pollGen?: number): void {
    if (pollGen != null) {
      this.positionGuard.onPollReceived(pollGen, ideas);
    }

    const activeIdeas = ideas.filter(idea => !this.isRecentlyDeleted(idea.id));

    for (const idea of activeIdeas) {
      if (idea.updatedAt) {
        const v = new Date(idea.updatedAt).getTime();
        const cur = this.ideaVersions.get(idea.id) || 0;
        if (v > cur) {
          this.ideaVersions.set(idea.id, v);
        }
      }
    }

    const currentItems = this.items();
    const nonIdeas = currentItems.filter(item => item.kind !== 'idea');
    const currentlyDragging = this.draggingIdea();

    const serverIdeas: IdeaItem[] = activeIdeas.map(idea => {
      const isBeingDragged = currentlyDragging !== null && currentlyDragging.id === idea.id;
      const currentLocal = currentItems.find(i => i.id === idea.id) as IdeaItem | undefined;
      const cached = this.localPositions.get(idea.id);
      const pending = this.positionGuard.getPendingMove(idea.id);

      let x: number;
      let y: number;

      if (isBeingDragged && currentLocal) {
        x = currentLocal.x;
        y = currentLocal.y;
      } else if (pending) {
        x = pending.x;
        y = pending.y;
      } else if (cached) {
        x = cached.x;
        y = cached.y;
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

      if (!pending && idea.x != null && idea.y != null) {
        this.positionGuard.trackConfirmedPosition(idea.id, idea.x, idea.y);
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

  private syncServerDrawings(drawings: DrawingResponse[], pollGen?: number): void {
    if (pollGen != null) {
      this.drawingPositionGuard.onPollReceived(pollGen, drawings);
    }

    const activeDrawings = drawings.filter(d => !this.isRecentlyDeletedDrawing(d.id));

    for (const d of activeDrawings) {
      if (d.updatedAt) {
        const v = new Date(d.updatedAt).getTime();
        const cur = this.drawingVersions.get(d.id) || 0;
        if (v > cur) {
          this.drawingVersions.set(d.id, v);
        }
      }
    }

    const currentItems = this.items();
    const currentDrawings = currentItems.filter(i => i.kind === 'stroke' || i.kind === 'shape');
    const ideaItems = currentItems.filter(i => i.kind === 'idea');

    const mappedDrawings: (StrokeItem | ShapeItem)[] = [];
    for (const d of activeDrawings) {
      const item = this.mapDrawingResponseToCanvasItem(d);
      if (!item) continue;

      if (this.draggingDrawing?.id === d.id) {
        item.offsetX = this.draggingDrawing.currentOffsetX;
        item.offsetY = this.draggingDrawing.currentOffsetY;
      } else {
        const pending = this.drawingPositionGuard.getPendingMove(d.id);
        if (pending) {
          item.offsetX = pending.offsetX;
          item.offsetY = pending.offsetY;
        } else {
          this.drawingPositionGuard.trackConfirmedPosition(d.id, d.offsetX || 0, d.offsetY || 0);
        }
      }
      mappedDrawings.push(item);
    }

    const isDifferent =
      currentDrawings.length !== mappedDrawings.length ||
      mappedDrawings.some((md, idx) => {
        const cd = currentDrawings[idx];
        return (
          !cd ||
          cd.id !== md.id ||
          (cd.offsetX || 0) !== (md.offsetX || 0) ||
          (cd.offsetY || 0) !== (md.offsetY || 0) ||
          cd.color !== md.color
        );
      });

    if (isDifferent) {
      this.items.set([...ideaItems, ...mappedDrawings]);
    }
  }

  private mapDrawingResponseToCanvasItem(d: DrawingResponse): StrokeItem | ShapeItem | null {
    try {
      const payload = JSON.parse(d.payload);
      const authorId = d.author?.id;
      const authorName = d.author?.username;
      const version = d.updatedAt ? new Date(d.updatedAt).getTime() : 0;

      if (d.kind === 'pen' || d.kind === 'highlighter') {
        const points: CanvasPoint[] = Array.isArray(payload) ? payload : (payload.points || []);
        return {
          kind: 'stroke',
          id: d.id,
          points,
          color: d.color,
          width: d.width,
          opacity: d.opacity,
          offsetX: d.offsetX || 0,
          offsetY: d.offsetY || 0,
          authorId,
          authorName,
          version,
          drawingKind: d.kind,
        };
      } else if (d.kind === 'triangle' || d.kind === 'circle' || d.kind === 'rectangle') {
        return {
          kind: 'shape',
          id: d.id,
          shape: d.kind,
          x: payload.x || 0,
          y: payload.y || 0,
          width: payload.width || 50,
          height: payload.height || 50,
          color: d.color,
          offsetX: d.offsetX || 0,
          offsetY: d.offsetY || 0,
          authorId,
          authorName,
          version,
        };
      }
    } catch (err) {
      console.error('Failed to parse drawing payload', d.id, err);
    }
    return null;
  }

  protected selectTool(tool: WorkspaceTool): void {
    if (tool === 'idea' && this.isRoomLocked()) return;
    this.selectedTool.set(tool);
    this.shapesOpen.set(tool === 'shapes');
    if (tool !== 'select') {
      this.selectedDrawingId.set(null);
    }
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
    this.selectedCanvasImageId.set(null);

    if (this.selectedTool() === 'select') {
      const surfaceWidth = surface.getBoundingClientRect().width;
      const tolerance = screenToleranceToCanvasUnits(6, surfaceWidth);
      const allItems = this.items();
      let hitItem: (StrokeItem | ShapeItem) | null = null;

      for (let i = allItems.length - 1; i >= 0; i--) {
        const item = allItems[i];
        if (item.kind === 'stroke') {
          if (hitTestStroke(point, item.points, item.width, tolerance, item.offsetX, item.offsetY)) {
            hitItem = item;
            break;
          }
        } else if (item.kind === 'shape') {
          if (hitTestShape(point, item, tolerance)) {
            hitItem = item;
            break;
          }
        }
      }

      if (hitItem) {
        if (this.isItemLocked('drawing', hitItem.id)) {
          const holder = this.getItemHolderName('drawing', hitItem.id) || 'another user';
          this.showTransientError(`This drawing is currently held by ${holder}.`);
          this.finishPointer(surface);
          return;
        }

        this.selectedDrawingId.set(hitItem.id);
        if (!this.isRoomLocked()) {
          this.draggingDrawing = {
            id: hitItem.id,
            startPointer: point,
            startOffsetX: hitItem.offsetX || 0,
            startOffsetY: hitItem.offsetY || 0,
            currentOffsetX: hitItem.offsetX || 0,
            currentOffsetY: hitItem.offsetY || 0,
            hasMoved: false,
          };
        }
      } else {
        this.selectedDrawingId.set(null);
      }
      return;
    }

    if (this.selectedTool() === 'idea') {
      if (this.room()?.closed) {
        this.finishPointer(surface);
        return;
      }
      this.ideaDraft.set({ x: point.x, y: point.y, title: '', description: '', categoryId: this.filterCategoryId() });
      this.ideaError.set('');
      this.finishPointer(surface);
      return;
    }

    if (this.selectedTool() === 'eraser') {
      this.eraseAt(point, surface.getBoundingClientRect().width);
      return;
    }

    if (this.selectedTool() === 'pen' || this.selectedTool() === 'highlighter') {
      if (this.isRoomLocked()) {
        this.finishPointer(surface);
        return;
      }
      this.currentStrokeId = typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : 'str-' + Date.now() + '-' + Math.random().toString(36).substring(2, 9);
      this.draftPoints = [point];
      const preview = this.createStroke();
      this.preview.set(preview);
      this.lastStrokePointsBroadcastTime = Date.now();
      this.realtime.sendStrokePoints(
        this.currentStrokeId,
        preview.color,
        preview.width,
        preview.opacity,
        this.draftPoints
      );
    }
  }

  protected onPointerMove(event: PointerEvent): void {
    if (event.pointerId !== this.pointerId) return;
    const surface = event.currentTarget as SVGElement;
    const point = this.toCanvasPoint(event, surface);

    if (this.selectedTool() === 'select') {
      if (this.draggingDrawing) {
        const dx = point.x - this.draggingDrawing.startPointer.x;
        const dy = point.y - this.draggingDrawing.startPointer.y;
        if (!this.draggingDrawing.hasMoved && Math.hypot(dx, dy) > 2) {
          this.draggingDrawing.hasMoved = true;
        }
        if (this.draggingDrawing.hasMoved) {
          const newOffsetX = Math.round(this.draggingDrawing.startOffsetX + dx);
          const newOffsetY = Math.round(this.draggingDrawing.startOffsetY + dy);
          this.draggingDrawing.currentOffsetX = newOffsetX;
          this.draggingDrawing.currentOffsetY = newOffsetY;

          this.items.update(items =>
            items.map(i => {
              if ((i.kind === 'stroke' || i.kind === 'shape') && i.id === this.draggingDrawing!.id) {
                return { ...i, offsetX: newOffsetX, offsetY: newOffsetY };
              }
              return i;
            })
          );

          this.realtime.broadcastDrawingMove(this.draggingDrawing.id, newOffsetX, newOffsetY, false);
        }
      }
      return;
    }

    if (this.selectedTool() === 'eraser') {
      this.eraseAt(point, surface.getBoundingClientRect().width);
      return;
    }

    if (!this.startPoint) return;
    if (this.selectedTool() === 'pen' || this.selectedTool() === 'highlighter') {
      this.draftPoints = [...this.draftPoints, point];
      const preview = this.createStroke();
      this.preview.set(preview);
      this.realtime.sendStrokePoints(
        this.currentStrokeId,
        preview.color,
        preview.width,
        preview.opacity,
        this.draftPoints
      );
    } else if (this.selectedTool() === 'shapes') {
      this.preview.set(this.createShape(this.startPoint, point));
    }
  }

  protected onPointerUp(event: PointerEvent): void {
    if (event.pointerId !== this.pointerId) return;
    const surface = event.currentTarget as SVGElement;
    const point = this.toCanvasPoint(event, surface);

    if (this.selectedTool() === 'select') {
      if (this.draggingDrawing) {
        const dd = this.draggingDrawing;
        this.draggingDrawing = null;
        if (dd.hasMoved) {
          this.realtime.broadcastDrawingMove(dd.id, dd.currentOffsetX, dd.currentOffsetY, true);

          const moveSeq = this.drawingPositionGuard.registerDrop(dd.id, dd.currentOffsetX, dd.currentOffsetY);
          const roomId = this.roomId();
          if (roomId) {
            this.drawingsService.moveDrawing(roomId, dd.id, {
              offsetX: dd.currentOffsetX,
              offsetY: dd.currentOffsetY,
            }).subscribe({
              next: updated => {
                this.drawingPositionGuard.onSaveSuccess(dd.id, moveSeq, updated.offsetX, updated.offsetY);
              },
              error: err => {
                console.error('Failed to move drawing', err);
                const rollback = this.drawingPositionGuard.onSaveFailure(dd.id, moveSeq);
                if (rollback.rolledBack) {
                  const rbX = rollback.rollbackPos?.offsetX ?? dd.startOffsetX;
                  const rbY = rollback.rollbackPos?.offsetY ?? dd.startOffsetY;
                  this.items.update(items =>
                    items.map(i => (i.kind === 'stroke' || i.kind === 'shape') && i.id === dd.id ? { ...i, offsetX: rbX, offsetY: rbY } : i)
                  );
                }
                this.showTransientError('Could not move drawing.');
              },
            });
          }

          this.realtime.sendObjectUnlock('drawing', dd.id);
        }
      }
      this.finishPointer(surface);
      return;
    }

    if (this.selectedTool() === 'pen' || this.selectedTool() === 'highlighter') {
      const strokeId = this.currentStrokeId;
      const simplified = simplifyPoints(this.draftPoints, 1.2);
      const isHighlighter = this.selectedTool() === 'highlighter';
      const color = this.selectedColor();
      const width = isHighlighter ? 30 : 5;
      const opacity = isHighlighter ? 0.35 : 1;

      const drawingCount = this.items().filter(i => i.kind !== 'idea').length;
      if (drawingCount >= 500) {
        this.showTransientError('Maximum of 500 drawings reached for this room.');
        this.finishPointer(surface);
        return;
      }

      const optimisticStroke: StrokeItem = {
        kind: 'stroke',
        id: strokeId,
        points: simplified,
        color,
        width,
        opacity,
        offsetX: 0,
        offsetY: 0,
        authorId: this.userService.currentUser()?.id,
        authorName: this.userService.currentUser()?.username,
        drawingKind: isHighlighter ? 'highlighter' : 'pen',
      };

      this.commit([...this.items(), optimisticStroke]);

      const roomId = this.roomId();
      if (roomId) {
        this.drawingsService.createDrawing(roomId, {
          kind: isHighlighter ? 'highlighter' : 'pen',
          color,
          width,
          opacity,
          payload: JSON.stringify(simplified),
          offsetX: 0,
          offsetY: 0,
        }).subscribe({
          next: created => {
            const ver = created.updatedAt ? new Date(created.updatedAt).getTime() : Date.now();
            this.drawingVersions.set(created.id, ver);
            this.items.update(items =>
              items.map(i => i.id === strokeId ? this.mapDrawingResponseToCanvasItem(created) ?? optimisticStroke : i)
            );
          },
          error: err => {
            console.error('Failed to create stroke on server', err);
            this.items.update(items => items.filter(i => i.id !== strokeId));
            this.showTransientError(err?.error?.message || 'Could not save drawing to server.');
          },
        });
      }
      this.finishPointer(surface);
      return;
    }

    if (this.selectedTool() === 'shapes') {
      if (this.startPoint) {
        const shapeKind = this.selectedShape();
        const x = Math.min(this.startPoint.x, point.x);
        const y = Math.min(this.startPoint.y, point.y);
        const width = Math.max(Math.abs(point.x - this.startPoint.x), 12);
        const height = Math.max(Math.abs(point.y - this.startPoint.y), 12);
        const color = this.selectedColor();
        const shapeId = typeof crypto.randomUUID === 'function'
          ? crypto.randomUUID()
          : 'shp-' + Date.now() + '-' + Math.random().toString(36).substring(2, 9);

        const drawingCount = this.items().filter(i => i.kind !== 'idea').length;
        if (drawingCount >= 500) {
          this.showTransientError('Maximum of 500 drawings reached for this room.');
          this.finishPointer(surface);
          return;
        }

        const optimisticShape: ShapeItem = {
          kind: 'shape',
          id: shapeId,
          shape: shapeKind,
          x,
          y,
          width,
          height,
          color,
          offsetX: 0,
          offsetY: 0,
          authorId: this.userService.currentUser()?.id,
          authorName: this.userService.currentUser()?.username,
        };

        this.commit([...this.items(), optimisticShape]);

        const roomId = this.roomId();
        if (roomId) {
          this.drawingsService.createDrawing(roomId, {
            kind: shapeKind,
            color,
            width: 2,
            opacity: 1,
            payload: JSON.stringify({ x, y, width, height }),
            offsetX: 0,
            offsetY: 0,
          }).subscribe({
            next: created => {
              const ver = created.updatedAt ? new Date(created.updatedAt).getTime() : Date.now();
              this.drawingVersions.set(created.id, ver);
              this.items.update(items =>
                items.map(i => i.id === shapeId ? this.mapDrawingResponseToCanvasItem(created) ?? optimisticShape : i)
              );
            },
            error: err => {
              console.error('Failed to create shape on server', err);
              this.items.update(items => items.filter(i => i.id !== shapeId));
              this.showTransientError(err?.error?.message || 'Could not save shape to server.');
            },
          });
        }
      }
      this.finishPointer(surface);
      return;
    }

    if (this.selectedTool() === 'eraser' && this.actionStartItems !== this.items()) {
      this.commit(this.items());
    }
    this.finishPointer(surface);
  }

  protected onIdeaPointerDown(event: PointerEvent, idea: IdeaItem): void {
    if (this.isRoomLocked()) return;
    if (event.button !== 0) return;

    if (this.selectedTool() === 'eraser') {
      event.stopPropagation();
      const isOwner = this.isRoomOwner();
      const isCreator = this.isCreator(idea);
      if (isOwner || isCreator) {
        this.eraseIdea(idea);
      } else {
        this.showTransientError('You can only erase your own ideas.');
      }
      return;
    }

    if (this.isItemLocked('card', idea.id)) {
      const holder = this.getItemHolderName('card', idea.id) || 'another user';
      this.showTransientError(`This idea is currently being moved by ${holder}.`);
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

      this.realtime.broadcastCardMove(
        {
          id: this.draggingIdea()!.id,
          x: newX,
          y: newY,
          by: this.userService.currentUser()?.id ?? 'anonymous',
        },
        false
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
      this.realtime.sendObjectUnlock('card', dragged.id);

      const currentItem = this.items().find(i => i.id === dragged.id);
      if (currentItem && currentItem.kind === 'idea') {
        this.localPositions.set(dragged.id, { x: currentItem.x, y: currentItem.y });
        this.saveLocalPositions();

        this.realtime.broadcastCardMove(
          {
            id: dragged.id,
            x: currentItem.x,
            y: currentItem.y,
            by: this.userService.currentUser()?.id ?? 'anonymous',
          },
          true
        );

        if (this.roomId()) {
          const moveSeq = this.positionGuard.registerDrop(dragged.id, currentItem.x, currentItem.y);
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
                const targetX = updated.x ?? currentItem.x;
                const targetY = updated.y ?? currentItem.y;
                this.positionGuard.onSaveSuccess(dragged.id, moveSeq, targetX, targetY);
                this.localPositions.set(dragged.id, { x: currentItem.x, y: currentItem.y });
                this.saveLocalPositions();
              },
              error: err => {
                console.error('Could not save dragged idea position', err);
                const rollback = this.positionGuard.onSaveFailure(dragged.id, moveSeq);
                if (rollback.rolledBack) {
                  const rollbackPos = rollback.rollbackPos ?? this.dragStartIdeaPos;
                  this.localPositions.set(dragged.id, { x: rollbackPos.x, y: rollbackPos.y });
                  this.saveLocalPositions();
                  this.items.update(items =>
                    items.map(item =>
                      item.kind === 'idea' && item.id === dragged.id
                        ? { ...item, x: rollbackPos.x, y: rollbackPos.y }
                        : item
                    )
                  );
                }
                this.ideaError.set('Could not update idea.');
                clearTimeout(this.ideaErrorTimer);
                this.ideaErrorTimer = window.setTimeout(() => {
                  if (this.ideaError() === 'Could not update idea.') {
                    this.ideaError.set('');
                  }
                }, 4000);
              },
            });
        }
      }
    }
  }

  private eraseIdea(idea: IdeaItem): void {
    if (this.room()?.closed) return;
    this.markDeleted(idea.id);
    if (this.activeCommentIdea()?.id === idea.id) {
      this.closeComments();
    }
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
    if (!draft || this.isRoomLocked()) return;
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
    if (this.isRoomLocked() || !this.isCreator(idea)) return;
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
    if (this.isRoomLocked() || !idea.id || !this.roomId()) return;

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
      id: this.currentStrokeId || String(this.nextItemId++),
      points: this.draftPoints,
      color: this.selectedColor(),
      width: highlighter ? 30 : 5,
      opacity: highlighter ? 0.35 : 1,
      offsetX: 0,
      offsetY: 0,
      drawingKind: highlighter ? 'highlighter' : 'pen',
    };
  }

  private createShape(start: CanvasPoint, end: CanvasPoint): ShapeItem {
    return {
      kind: 'shape',
      id: String(this.nextItemId++),
      shape: this.selectedShape(),
      x: Math.min(start.x, end.x),
      y: Math.min(start.y, end.y),
      width: Math.max(Math.abs(end.x - start.x), 12),
      height: Math.max(Math.abs(end.y - start.y), 12),
      color: this.selectedColor(),
      offsetX: 0,
      offsetY: 0,
    };
  }

  protected onStagePointerMove(event: PointerEvent): void {
    const stage = event.currentTarget as HTMLElement;
    const bounds = stage.getBoundingClientRect();
    if (bounds.width <= 0 || bounds.height <= 0) return;
    const x = Math.max(0, Math.min(1000, ((event.clientX - bounds.left) / bounds.width) * 1000));
    const y = Math.max(0, Math.min(500, ((event.clientY - bounds.top) / bounds.height) * 500));
    this.realtime.sendCursorMove(Math.round(x * 10) / 10, Math.round(y * 10) / 10);
  }

  protected onStagePointerLeave(event: PointerEvent): void {
    this.realtime.sendCursorLeave();
  }

  private eraseAt(point: CanvasPoint, surfaceWidth = 1000): void {
    if (this.isRoomLocked()) return;
    const currentRoomId = this.roomId();
    const currentUserId = this.userService.currentUser()?.id;
    const isOwner = this.isRoomOwner();
    const tolerance = screenToleranceToCanvasUnits(12, surfaceWidth);

    const allItems = this.items();
    const drawingsToDelete: (StrokeItem | ShapeItem)[] = [];
    let unauthorizedDrawingHit = false;

    for (const item of allItems) {
      if (item.kind === 'stroke') {
        if (hitTestStroke(point, item.points, item.width, tolerance, item.offsetX, item.offsetY)) {
          const canDelete = isOwner || (item.authorId != null && item.authorId === currentUserId);
          if (canDelete) {
            drawingsToDelete.push(item);
          } else {
            unauthorizedDrawingHit = true;
          }
        }
      } else if (item.kind === 'shape') {
        if (hitTestShape(point, item, tolerance)) {
          const canDelete = isOwner || (item.authorId != null && item.authorId === currentUserId);
          if (canDelete) {
            drawingsToDelete.push(item);
          } else {
            unauthorizedDrawingHit = true;
          }
        }
      }
    }

    if (unauthorizedDrawingHit && drawingsToDelete.length === 0) {
      this.showTransientError('You can only erase your own drawings.');
    }

    const ideasToDelete: IdeaItem[] = [];
    let unauthorizedIdeaHit = false;
    for (const item of allItems) {
      if (item.kind === 'idea') {
        if (Math.abs(item.x - point.x) < 70 && Math.abs(item.y - point.y) < 35) {
          const canDelete = isOwner || this.isCreator(item);
          if (canDelete) {
            ideasToDelete.push(item);
          } else {
            unauthorizedIdeaHit = true;
          }
        }
      }
    }

    if (unauthorizedIdeaHit && ideasToDelete.length === 0) {
      this.showTransientError('You can only erase your own ideas.');
    }

    if (drawingsToDelete.length > 0) {
      for (const d of drawingsToDelete) {
        this.markDrawingDeleted(d.id);
        if (this.selectedDrawingId() === d.id) {
          this.selectedDrawingId.set(null);
        }
        if (currentRoomId) {
          this.drawingsService.deleteDrawing(currentRoomId, d.id).subscribe({
            error: err => console.error('Could not delete drawing', d.id, err),
          });
        }
      }
      const toDeleteIds = new Set(drawingsToDelete.map(d => d.id));
      this.items.update(items => items.filter(i => !toDeleteIds.has(i.id)));
    }

    if (ideasToDelete.length > 0) {
      for (const erased of ideasToDelete) {
        this.eraseIdea(erased);
      }
    }
  }

  @HostListener('window:keydown', ['$event'])
  protected handleKeyDown(event: KeyboardEvent): void {
    const target = event.target as HTMLElement | null;
    const isInput = target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable);
    if (isInput) return;

    if (event.key === 'Escape') {
      this.selectedDrawingId.set(null);
    } else if (event.key === 'Delete' || event.key === 'Backspace') {
      const selId = this.selectedDrawingId();
      if (selId) {
        const drawing = this.items().find(i => (i.kind === 'stroke' || i.kind === 'shape') && i.id === selId) as (StrokeItem | ShapeItem) | undefined;
        if (drawing) {
          const isOwner = this.isRoomOwner();
          const currentUserId = this.userService.currentUser()?.id;
          const canDelete = isOwner || (drawing.authorId != null && drawing.authorId === currentUserId);
          if (canDelete) {
            this.markDrawingDeleted(selId);
            this.selectedDrawingId.set(null);
            this.items.update(items => items.filter(i => i.id !== selId));
            const roomId = this.roomId();
            if (roomId) {
              this.drawingsService.deleteDrawing(roomId, selId).subscribe({
                error: err => console.error('Failed to delete drawing', err),
              });
            }
          } else {
            this.showTransientError('You can only erase your own drawings.');
          }
        }
      }
    }
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

  protected toggleLeftPanel(): void {
    this.leftPanelCollapsed.update(v => !v);
  }

  protected toggleAiDrawer(): void {
    this.saveExportMenuOpen.set(false);
    this.aiDrawerOpen.update(v => !v);
    if (!this.aiDrawerOpen()) {
      this.aiSelectedIdea.set(null);
    }
  }

  protected closeAiDrawer(): void {
    this.aiDrawerOpen.set(false);
    this.aiSelectedIdea.set(null);
  }

  protected openAiForIdea(event: MouseEvent, idea: IdeaItem): void {
    event.stopPropagation();
    this.aiSelectedIdea.set(idea);
    this.aiDrawerOpen.set(true);
  }

  protected onAiAddIdeaToCanvas(payload: { title: string; description?: string }): void {
    if (this.isRoomLocked()) return;
    const currentRoomId = this.roomId();
    const title = payload.title.trim();
    const description = (payload.description || '').trim();
    if (!title) return;

    // Calculate placement: safe area within 1000x500 board bounds
    const x = Math.floor(250 + Math.random() * 450);
    const y = Math.floor(150 + Math.random() * 200);

    if (!currentRoomId) {
      const localIdea: IdeaItem = {
        kind: 'idea',
        id: String(this.nextItemId++),
        x,
        y,
        color: this.selectedColor(),
        creatorId: this.userService.currentUser()?.id ?? null,
        title,
        description,
        categoryId: null,
        categoryTitle: null,
        category: null,
        voteCount: 0,
        voted: false,
      };
      this.localPositions.set(localIdea.id, { x, y });
      this.saveLocalPositions();
      this.commit([...this.items(), localIdea]);
      return;
    }

    this.mindspark
      .createIdea(currentRoomId, {
        title,
        description,
        x,
        y,
        color: this.selectedColor(),
        categoryId: null,
      })
      .subscribe({
        next: newIdea => {
          const ideaItem: IdeaItem = {
            kind: 'idea',
            id: newIdea.id,
            x: newIdea.x ?? x,
            y: newIdea.y ?? y,
            color: newIdea.color || this.selectedColor(),
            creatorId: newIdea.author?.id ?? this.userService.currentUser()?.id ?? null,
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
        },
        error: () => {
          this.showTransientError('Could not add AI idea to canvas.');
        },
      });
  }

  protected onAiUpdateIdeaOnCanvas(payload: { id: string; title: string; description?: string }): void {
    if (this.isRoomLocked()) return;
    const currentRoomId = this.roomId();
    const ideaId = payload.id;
    const title = payload.title.trim();
    const description = (payload.description || '').trim();

    if (!currentRoomId) {
      this.commit(
        this.items().map(item =>
          item.kind === 'idea' && item.id === ideaId
            ? { ...item, title, description }
            : item
        )
      );
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
      },
      error: () => {
        this.showTransientError('Could not update idea from AI.');
      },
    });
  }

  protected toggleSaveExportMenu(): void {
    this.saveExportMenuOpen.update(v => !v);
  }

  protected closeSaveExportMenu(): void {
    this.saveExportMenuOpen.set(false);
  }

  protected openSaveSnapshotModal(): void {
    this.closeSaveExportMenu();
    const activeAi = this.aiDrawerComponent?.getActiveSummaryOrActionPlan() || null;
    this.availableAiSummary.set(activeAi);
    this.includeAiSummary.set(!!activeAi);
    const roomName = this.room()?.name || 'Session Snapshot';
    this.saveSnapshotTitle.set(roomName);
    this.saveSnapshotNotes.set('');
    this.saveModalOpen.set(true);
  }

  protected closeSaveSnapshotModal(): void {
    this.saveModalOpen.set(false);
  }

  protected submitSaveSnapshot(): void {
    const roomId = this.roomId();
    if (!roomId) return;

    this.isSavingSnapshot.set(true);
    const title = this.saveSnapshotTitle().trim();
    const notes = this.saveSnapshotNotes().trim();
    const aiSummary = this.includeAiSummary() ? this.availableAiSummary() || undefined : undefined;

    this.sessionHistory.saveSnapshot({
      roomId,
      title: title || undefined,
      notes: notes || undefined,
      aiSummary,
    }).subscribe({
      next: saved => {
        this.isSavingSnapshot.set(false);
        this.saveModalOpen.set(false);
        this.savedToast.set({ id: saved.id, title: saved.title });
        setTimeout(() => {
          if (this.savedToast()?.id === saved.id) {
            this.savedToast.set(null);
          }
        }, 6000);
      },
      error: () => {
        this.isSavingSnapshot.set(false);
        this.showTransientError('Failed to save session snapshot.');
      },
    });
  }

  protected onExportLive(format: ExportFormat): void {
    this.closeSaveExportMenu();
    const roomId = this.roomId();
    if (!roomId) return;
    this.sessionHistory.exportLiveRoom(roomId, format).subscribe({
      error: () => {
        this.showTransientError('Failed to export room.');
      },
    });
  }

  protected onPrintLiveRoom(): void {
    this.closeSaveExportMenu();
    window.print();
  }

  // =========================================================================
  // VOTE POOL (Story 37)
  // =========================================================================

  protected openStartVotePoolModal(): void {
    if (this.isRoomLocked() || !this.isRoomOwner()) return;
    this.votePoolTitle.set('');
    this.votePoolVotesPerParticipant.set(3);
    this.votePoolAllowMultiple.set(true);
    this.votePoolModalError.set('');
    this.showStartVotePoolModal.set(true);
  }

  protected closeStartVotePoolModal(): void {
    this.showStartVotePoolModal.set(false);
    this.votePoolModalError.set('');
  }

  protected submitStartVotePool(): void {
    const currentRoomId = this.roomId();
    const title = this.votePoolTitle().trim();
    const votesPer = Math.max(1, Math.min(20, Math.round(this.votePoolVotesPerParticipant())));
    if (!title) {
      this.votePoolModalError.set('Pool title is required.');
      return;
    }

    this.votePoolSubmitting.set(true);
    this.votePoolModalError.set('');
    this.votePoolService.createPool(currentRoomId, {
      title,
      votesPerParticipant: votesPer,
      allowMultipleVotesPerIdea: this.votePoolAllowMultiple(),
    }).subscribe({
      next: () => {
        this.votePoolSubmitting.set(false);
        this.showStartVotePoolModal.set(false);
      },
      error: err => {
        this.votePoolSubmitting.set(false);
        this.votePoolModalError.set(err.error?.message || err.message || 'Could not start vote pool.');
      }
    });
  }

  protected castPoolVote(event: Event, idea: IdeaItem): void {
    event.stopPropagation();
    const pool = this.activeVotePool();
    if (this.isRoomLocked() || !pool || pool.status !== 'OPEN') return;
    if (this.myVotesRemaining() <= 0) {
      this.showTransientError('You have used all your pool votes.');
      return;
    }
    if (!pool.allowMultipleVotesPerIdea && this.myVotesOnIdea(idea.id) > 0) {
      this.showTransientError('Only 1 vote per idea is allowed in this pool.');
      return;
    }

    this.votePoolService.castVote(this.roomId(), pool.id, idea.id).subscribe({
      error: err => {
        const msg = err.error?.message || err.error?.error || 'Failed to cast pool vote.';
        this.showTransientError(msg);
      }
    });
  }

  protected removePoolVote(event: Event, idea: IdeaItem): void {
    event.stopPropagation();
    const pool = this.activeVotePool();
    if (this.isRoomLocked() || !pool || pool.status !== 'OPEN') return;
    if (this.myVotesOnIdea(idea.id) <= 0) return;

    this.votePoolService.removeVote(this.roomId(), pool.id, idea.id).subscribe({
      error: err => {
        const msg = err.error?.message || 'Failed to remove vote.';
        this.showTransientError(msg);
      }
    });
  }

  protected closeCurrentVotePool(): void {
    const pool = this.activeVotePool();
    if (this.isRoomLocked() || !pool || !this.isRoomOwner()) return;
    const confirmed = window.confirm(`End the vote pool "${pool.title}" and lock final results?`);
    if (!confirmed) return;

    this.votePoolService.closePool(this.roomId(), pool.id).subscribe({
      next: () => {
        this.showVotePoolResultsModal.set(true);
      },
      error: err => {
        this.showTransientError(err.error?.message || 'Failed to close vote pool.');
      }
    });
  }

  protected openVotePoolResultsModal(): void {
    this.showVotePoolResultsModal.set(true);
  }

  protected closeVotePoolResultsModal(): void {
    this.showVotePoolResultsModal.set(false);
  }

  protected myVotesOnIdea(ideaId: string): number {
    return this.activeVotePool()?.myVotesByIdeaId?.[ideaId] ?? 0;
  }

  // =========================================================================
  // IDEA ATTACHMENTS & LIGHTBOX (Story 26)
  // =========================================================================

  protected getIdeaAttachments(ideaId: string): IdeaAttachment[] {
    return this.ideaAttachments().get(ideaId) || [];
  }

  protected triggerIdeaAttach(event: Event, idea: IdeaItem, fileInput: HTMLInputElement): void {
    event.stopPropagation();
    if (this.isRoomLocked()) return;
    if (this.getIdeaAttachments(idea.id).length >= 3) {
      this.showTransientError('Maximum 3 images per idea.');
      return;
    }
    this.targetIdeaForAttach.set(idea);
    fileInput.value = '';
    fileInput.click();
  }

  protected onIdeaImageFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    const idea = this.targetIdeaForAttach();
    if (!file || !idea) return;

    const validationErr = this.mediaService.validateClientSide(file);
    if (validationErr) {
      this.showTransientError(validationErr);
      return;
    }

    this.isUploadingMedia.set(true);
    this.mediaService.uploadRoomImage(this.roomId(), file).subscribe({
      next: mediaResp => {
        this.roomMediaService.addIdeaAttachment(this.roomId(), idea.id, {
          mediaId: mediaResp.mediaId,
          contentType: mediaResp.contentType,
          width: mediaResp.width,
          height: mediaResp.height,
        }).subscribe({
          next: att => {
            this.isUploadingMedia.set(false);
            this.mediaService.fetchImageBlobUrl(this.roomId(), att.mediaId).subscribe();
          },
          error: err => {
            this.isUploadingMedia.set(false);
            this.showTransientError(err.error?.message || 'Failed to attach image to idea.');
          }
        });
      },
      error: err => {
        this.isUploadingMedia.set(false);
        this.showTransientError(err.error?.message || err.message || 'Failed to upload image.');
      }
    });
  }

  protected openLightbox(event: Event, att: IdeaAttachment): void {
    event.stopPropagation();
    this.lightboxAttachment.set(att);
    const myId = this.userService.currentUser()?.id;
    const isOwner = this.isRoomOwner();
    const isUploader = myId != null && att.uploadedById === myId;
    this.lightboxCanDelete.set(isOwner || isUploader);
  }

  protected closeLightbox(): void {
    this.lightboxAttachment.set(null);
  }

  protected deleteLightboxAttachment(): void {
    const att = this.lightboxAttachment();
    if (!att || this.isRoomLocked()) return;
    this.roomMediaService.deleteIdeaAttachment(this.roomId(), att.ideaId, att.id).subscribe({
      next: () => {
        this.closeLightbox();
      },
      error: err => {
        this.showTransientError(err.error?.message || 'Failed to delete attachment.');
      }
    });
  }

  // =========================================================================
  // CANVAS STANDALONE IMAGES (Story 26)
  // =========================================================================

  protected triggerCanvasImageUpload(fileInput: HTMLInputElement): void {
    if (this.isRoomLocked()) return;
    if (this.canvasImages().length >= 15) {
      this.showTransientError('Maximum of 15 canvas images reached for this room.');
      return;
    }
    fileInput.value = '';
    fileInput.click();
  }

  protected onCanvasImageFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    const valErr = this.mediaService.validateClientSide(file);
    if (valErr) {
      this.showTransientError(valErr);
      return;
    }

    this.isUploadingMedia.set(true);
    this.mediaService.uploadRoomImage(this.roomId(), file).subscribe({
      next: mediaResp => {
        let w = 240;
        let h = 180;
        if (mediaResp.width && mediaResp.height) {
          const ratio = mediaResp.width / mediaResp.height;
          if (ratio >= 1) {
            w = 240;
            h = Math.round(240 / ratio);
          } else {
            h = 240;
            w = Math.round(240 * ratio);
          }
        }
        w = Math.max(60, Math.min(1200, w));
        h = Math.max(60, Math.min(1200, h));

        this.roomMediaService.addCanvasImage(this.roomId(), {
          mediaId: mediaResp.mediaId,
          contentType: mediaResp.contentType,
          x: 380,
          y: 160,
          width: w,
          height: h,
          naturalWidth: mediaResp.width || w,
          naturalHeight: mediaResp.height || h,
          zIndex: 1,
        }).subscribe({
          next: canvasImg => {
            this.isUploadingMedia.set(false);
            this.mediaService.fetchImageBlobUrl(this.roomId(), canvasImg.mediaId).subscribe();
          },
          error: err => {
            this.isUploadingMedia.set(false);
            this.showTransientError(err.error?.message || 'Failed to place image on canvas.');
          }
        });
      },
      error: err => {
        this.isUploadingMedia.set(false);
        this.showTransientError(err.error?.message || err.message || 'Failed to upload canvas image.');
      }
    });
  }

  protected onCanvasImagePointerDown(event: PointerEvent, img: CanvasImageItem): void {
    if (this.isRoomLocked() || event.button !== 0) return;

    if (this.selectedTool() === 'eraser') {
      event.stopPropagation();
      const myId = this.userService.currentUser()?.id;
      const isOwner = this.isRoomOwner();
      const isUploader = myId != null && img.uploadedById === myId;
      if (isOwner || isUploader) {
        this.deleteCanvasImage(event, img);
      } else {
        this.showTransientError('You can only erase your own images.');
      }
      return;
    }

    event.stopPropagation();
    this.selectedCanvasImageId.set(img.id);
    const el = event.currentTarget as HTMLElement;
    el.setPointerCapture(event.pointerId);
    this.activeCanvasImageDragPointerId = event.pointerId;
    this.canvasImageDragStartPointer = { x: event.clientX, y: event.clientY };
    this.canvasImageDragStartPos = { x: img.x, y: img.y };
    this.canvasImageHasDragged = false;
  }

  protected onCanvasImagePointerMove(event: PointerEvent): void {
    if (this.isRoomLocked() || this.activeCanvasImageDragPointerId !== event.pointerId) return;

    const dxPixels = event.clientX - this.canvasImageDragStartPointer.x;
    const dyPixels = event.clientY - this.canvasImageDragStartPointer.y;

    if (!this.canvasImageHasDragged && Math.hypot(dxPixels, dyPixels) > 3) {
      this.canvasImageHasDragged = true;
    }

    if (this.canvasImageHasDragged) {
      const stage = (event.currentTarget as HTMLElement).closest('.canvas-stage') as HTMLElement | null;
      const bounds = stage ? stage.getBoundingClientRect() : { width: 1000, height: 500 };
      const dxCanvas = (dxPixels / bounds.width) * 1000;
      const dyCanvas = (dyPixels / bounds.height) * 500;

      const imgId = this.selectedCanvasImageId();
      const target = this.canvasImages().find(i => i.id === imgId);
      const w = target ? target.width : 200;
      const h = target ? target.height : 150;

      const newX = Math.round(Math.max(0, Math.min(1000 - w, this.canvasImageDragStartPos.x + dxCanvas)));
      const newY = Math.round(Math.max(0, Math.min(500 - h, this.canvasImageDragStartPos.y + dyCanvas)));

      this.canvasImages.update(list =>
        list.map(i => i.id === imgId ? { ...i, x: newX, y: newY } : i)
      );
    }
  }

  protected onCanvasImagePointerUp(event: PointerEvent): void {
    if (this.activeCanvasImageDragPointerId !== event.pointerId) return;
    const el = event.currentTarget as HTMLElement;
    try {
      el.releasePointerCapture(event.pointerId);
    } catch {}

    const imgId = this.selectedCanvasImageId();
    const wasDragged = this.canvasImageHasDragged;
    this.activeCanvasImageDragPointerId = null;
    this.canvasImageHasDragged = false;

    if (wasDragged && imgId) {
      const updated = this.canvasImages().find(i => i.id === imgId);
      if (updated) {
        this.roomMediaService.updateCanvasImage(this.roomId(), updated.id, {
          x: updated.x,
          y: updated.y,
          width: updated.width,
          height: updated.height,
          zIndex: updated.zIndex,
        }).subscribe({
          error: (err) => console.error('Failed to persist canvas image position', err)
        });
      }
    }
  }

  protected onCanvasImageResizeStart(event: PointerEvent, img: CanvasImageItem): void {
    if (this.isRoomLocked() || event.button !== 0) return;
    event.stopPropagation();
    const el = event.currentTarget as HTMLElement;
    el.setPointerCapture(event.pointerId);
    this.activeCanvasImageResizePointerId = event.pointerId;
    this.canvasImageResizeStartPointer = { x: event.clientX, y: event.clientY };
    this.canvasImageResizeStartSize = { width: img.width, height: img.height };
    this.canvasImageResizeItem = img;
  }

  protected onCanvasImageResizeMove(event: PointerEvent): void {
    if (this.isRoomLocked() || this.activeCanvasImageResizePointerId !== event.pointerId || !this.canvasImageResizeItem) return;

    const dxPixels = event.clientX - this.canvasImageResizeStartPointer.x;
    const dyPixels = event.clientY - this.canvasImageResizeStartPointer.y;

    const stage = (event.currentTarget as HTMLElement).closest('.canvas-stage') as HTMLElement | null;
    const bounds = stage ? stage.getBoundingClientRect() : { width: 1000, height: 500 };
    const dxCanvas = (dxPixels / bounds.width) * 1000;
    const dyCanvas = (dyPixels / bounds.height) * 500;

    const newW = Math.round(Math.max(60, Math.min(1200, this.canvasImageResizeStartSize.width + dxCanvas)));
    const newH = Math.round(Math.max(60, Math.min(1200, this.canvasImageResizeStartSize.height + dyCanvas)));

    const imgId = this.canvasImageResizeItem.id;
    this.canvasImages.update(list =>
      list.map(i => i.id === imgId ? { ...i, width: newW, height: newH } : i)
    );
  }

  protected onCanvasImageResizeUp(event: PointerEvent): void {
    if (this.activeCanvasImageResizePointerId !== event.pointerId) return;
    const el = event.currentTarget as HTMLElement;
    try {
      el.releasePointerCapture(event.pointerId);
    } catch {}

    const img = this.canvasImageResizeItem;
    this.activeCanvasImageResizePointerId = null;
    this.canvasImageResizeItem = null;

    if (img) {
      const current = this.canvasImages().find(i => i.id === img.id);
      if (current) {
        this.roomMediaService.updateCanvasImage(this.roomId(), current.id, {
          x: current.x,
          y: current.y,
          width: current.width,
          height: current.height,
          zIndex: current.zIndex,
        }).subscribe({
          error: (err) => console.error('Failed to persist canvas image resize', err)
        });
      }
    }
  }

  protected deleteCanvasImage(event: Event, img: CanvasImageItem): void {
    event.stopPropagation();
    if (this.isRoomLocked()) return;
    this.roomMediaService.deleteCanvasImage(this.roomId(), img.id).subscribe({
      next: () => {
        if (this.selectedCanvasImageId() === img.id) {
          this.selectedCanvasImageId.set(null);
        }
      },
      error: err => this.showTransientError(err.error?.message || 'Failed to delete canvas image.')
    });
  }

  private syncRoomMediaBundle(bundle: RoomMediaBundle): void {
    if (!bundle) return;
    const attMap = new Map<string, IdeaAttachment[]>();
    if (bundle.ideaAttachmentsByIdeaId) {
      for (const [ideaId, list] of Object.entries(bundle.ideaAttachmentsByIdeaId)) {
        attMap.set(ideaId, list);
        for (const att of list) {
          this.mediaService.fetchImageBlobUrl(this.roomId(), att.mediaId).subscribe();
        }
      }
    }
    this.ideaAttachments.set(attMap);

    const isDragging = this.activeCanvasImageDragPointerId !== null;
    const currentList = this.canvasImages();
    const mapped: CanvasImageItem[] = (bundle.canvasImages || []).map(ci => {
      const existing = currentList.find(i => i.id === ci.id);
      this.mediaService.fetchImageBlobUrl(this.roomId(), ci.mediaId).subscribe();
      if (isDragging && existing && existing.id === this.selectedCanvasImageId()) {
        return existing;
      }
      return {
        kind: 'image',
        id: ci.id,
        mediaId: ci.mediaId,
        x: ci.x,
        y: ci.y,
        width: ci.width,
        height: ci.height,
        naturalWidth: ci.naturalWidth,
        naturalHeight: ci.naturalHeight,
        zIndex: ci.zIndex,
        contentType: ci.contentType,
        uploadedById: ci.uploadedById,
        uploadedByName: ci.uploadedByName,
        uploadedBySub: ci.uploadedBySub,
        createdAt: ci.createdAt,
      };
    });
    this.canvasImages.set(mapped);
  }
}
