import { Component, HostListener, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { SessionHistoryService } from '../../../../core/sessions/session-history.service';
import { ExportFormat, SessionSnapshotSummary } from '../../../../core/sessions/session.models';

@Component({
  selector: 'app-sessions-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './sessions-list.html',
  styleUrl: './sessions-list.scss',
})
export class SessionsListComponent implements OnInit {
  protected readonly sessionService = inject(SessionHistoryService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  searchQuery = '';
  roomIdFilter: string | null = null;
  readonly activeExportId = signal<string | null>(null);
  readonly deleteTarget = signal<SessionSnapshotSummary | null>(null);

  private searchDebounce?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    this.route.queryParamMap.subscribe(params => {
      this.roomIdFilter = params.get('roomId');
      this.searchQuery = params.get('q') || '';
      this.load();
    });
  }

  load(): void {
    this.sessionService.listSnapshots(this.roomIdFilter || undefined, this.searchQuery || undefined).subscribe();
  }

  onSearchChange(): void {
    if (this.searchDebounce) {
      clearTimeout(this.searchDebounce);
    }
    this.searchDebounce = setTimeout(() => {
      this.updateQueryParams();
      this.load();
    }, 300);
  }

  clearRoomFilter(): void {
    this.roomIdFilter = null;
    this.updateQueryParams();
    this.load();
  }

  resetFilters(): void {
    this.searchQuery = '';
    this.roomIdFilter = null;
    this.updateQueryParams();
    this.load();
  }

  refresh(): void {
    this.load();
  }

  toggleExportMenu(id: string, event: Event): void {
    event.stopPropagation();
    if (this.activeExportId() === id) {
      this.activeExportId.set(null);
    } else {
      this.activeExportId.set(id);
    }
  }

  @HostListener('document:click')
  closeExportMenus(): void {
    this.activeExportId.set(null);
  }

  exportSession(id: string, format: ExportFormat): void {
    this.activeExportId.set(null);
    this.sessionService.exportSnapshot(id, format).subscribe({
      error: () => {},
    });
  }

  confirmDelete(snapshot: SessionSnapshotSummary): void {
    this.deleteTarget.set(snapshot);
  }

  cancelDelete(): void {
    this.deleteTarget.set(null);
  }

  executeDelete(): void {
    const target = this.deleteTarget();
    if (!target) return;

    this.sessionService.deleteSnapshot(target.id).subscribe({
      next: () => {
        this.deleteTarget.set(null);
      },
      error: () => {
        this.deleteTarget.set(null);
      },
    });
  }

  private updateQueryParams(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        roomId: this.roomIdFilter || null,
        q: this.searchQuery || null,
      },
      queryParamsHandling: 'merge',
    });
  }
}
