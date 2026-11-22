import { Component, OnInit, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { SessionHistoryService } from '../../../../core/sessions/session-history.service';
import { ExportFormat, IdeaExportDto } from '../../../../core/sessions/session.models';

interface CategoryGroup {
  id: number;
  name: string;
  ideas: IdeaExportDto[];
}

@Component({
  selector: 'app-session-detail',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './session-detail.html',
  styleUrl: './session-detail.scss',
})
export class SessionDetailComponent implements OnInit {
  protected readonly sessionService = inject(SessionHistoryService);
  private readonly route = inject(ActivatedRoute);

  private sessionId = '';

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const id = params.get('id');
      if (id) {
        this.sessionId = id;
        this.sessionService.getSnapshot(id).subscribe();
      }
    });
  }

  readonly topVotedIdeas = computed<IdeaExportDto[]>(() => {
    const detail = this.sessionService.selectedSnapshot();
    if (!detail?.snapshotData?.ideas) return [];
    return [...detail.snapshotData.ideas]
      .sort((a, b) => b.voteCount - a.voteCount)
      .slice(0, 5);
  });

  readonly groupedCategories = computed<CategoryGroup[]>(() => {
    const detail = this.sessionService.selectedSnapshot();
    if (!detail?.snapshotData) return [];

    const categories = detail.snapshotData.categories || [];
    const ideas = detail.snapshotData.ideas || [];

    return categories.map(cat => ({
      id: cat.id,
      name: cat.name,
      ideas: ideas.filter(i => i.categoryId === cat.id),
    }));
  });

  readonly uncategorizedIdeas = computed<IdeaExportDto[]>(() => {
    const detail = this.sessionService.selectedSnapshot();
    if (!detail?.snapshotData?.ideas) return [];
    return detail.snapshotData.ideas.filter(i => i.categoryId == null);
  });

  exportSession(format: ExportFormat): void {
    if (!this.sessionId) return;
    this.sessionService.exportSnapshot(this.sessionId, format).subscribe();
  }

  printReport(): void {
    window.print();
  }
}
