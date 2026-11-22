import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  AI_CAPS,
  ActionPlanResult,
  AiResponse,
  AiRoomIdea,
  CompareIdeasResult,
  FindRisksResult,
  GenerateIdeasResult,
  GroupIdeasResult,
  ImproveIdeaMode,
  ImproveIdeaResult,
  SummarizeSessionResult,
} from '../../../../core/ai/ai.models';
import { AiService } from '../../../../core/ai/ai.service';

export type AiModeTab = 'room' | 'idea';
export type RoomAiTool = 'generate' | 'group' | 'summarize' | 'compare';
export type IdeaAiTool = 'improve' | 'action-plan' | 'risks';

@Component({
  selector: 'app-ai-drawer',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ai-drawer.html',
  styleUrl: './ai-drawer.scss',
})
export class AiDrawerComponent implements OnChanges, OnDestroy {
  protected readonly ai = inject(AiService);

  @Input() isOpen = false;
  @Input() roomId = '';
  @Input() ideas: AiRoomIdea[] = [];
  @Input() selectedIdea: AiRoomIdea | null = null;
  @Input() isRoomLocked = false;

  @Output() closeDrawer = new EventEmitter<void>();
  @Output() addIdeaToCanvas = new EventEmitter<{ title: string; description?: string }>();
  @Output() updateIdeaOnCanvas = new EventEmitter<{ id: string; title: string; description?: string }>();

  protected readonly caps = AI_CAPS;

  // Active navigation
  protected readonly activeMode = signal<AiModeTab>('room');
  protected readonly activeRoomTool = signal<RoomAiTool>('generate');
  protected readonly activeIdeaTool = signal<IdeaAiTool>('improve');

  // Form states - Generate
  protected generateTopic = '';
  protected generateContext = '';
  protected generateCount = 5;

  // Form states - Improve
  protected improveMode: ImproveIdeaMode = 'improve';
  protected selectedIdeaId = '';

  // Form states - Action Plan
  protected actionPlanGoal = '';

  // Form states - Risks
  protected risksContext = '';

  // Form states - Compare
  protected readonly compareSelectedIds = signal<string[]>([]);

  // Form states - Summarize
  protected summarizeSessionTitle = '';

  // Results
  protected generateResult = signal<AiResponse<GenerateIdeasResult> | null>(null);
  protected groupResult = signal<AiResponse<GroupIdeasResult> | null>(null);
  protected summarizeResult = signal<AiResponse<SummarizeSessionResult> | null>(null);
  protected compareResult = signal<AiResponse<CompareIdeasResult> | null>(null);
  protected improveResult = signal<AiResponse<ImproveIdeaResult> | null>(null);
  protected actionPlanResult = signal<AiResponse<ActionPlanResult> | null>(null);
  protected risksResult = signal<AiResponse<FindRisksResult> | null>(null);

  // Added ideas feedback tracker: Map of title -> boolean
  protected readonly addedItems = signal<Record<string, boolean>>({});

  public getActiveSummaryOrActionPlan(): string | null {
    if (this.summarizeResult()?.result?.summary) {
      return this.summarizeResult()!.result!.summary;
    }
    if (this.actionPlanResult()?.result) {
      const plan = this.actionPlanResult()!.result;
      const stepsText = plan.steps?.map(s => `${s.order}. ${s.title}: ${s.description}`).join('\n') || '';
      return `${plan.title}\n\n${stepsText}`;
    }
    return null;
  }

  // Copy feedback tracker
  protected readonly copySuccess = signal<string | null>(null);

  // Busy retry timer
  protected readonly retryCountdown = signal<number>(0);
  private countdownInterval?: ReturnType<typeof setInterval>;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['isOpen'] && this.isOpen) {
      // Lazy load usage and user tier on open
      this.ai.loadUserAndUsage().subscribe({ error: () => {} });
    }

    if (changes['selectedIdea'] && this.selectedIdea) {
      this.selectedIdeaId = this.selectedIdea.id;
      this.activeMode.set('idea');
    }

    if (changes['ideas'] && this.ideas) {
      // Clean up compare selection if ideas were removed
      const currentValidIds = new Set(this.ideas.map(i => i.id));
      const filtered = this.compareSelectedIds().filter(id => currentValidIds.has(id));
      if (filtered.length !== this.compareSelectedIds().length) {
        this.compareSelectedIds.set(filtered);
      }
    }
  }

  ngOnDestroy(): void {
    this.stopCountdown();
  }

  protected setMode(mode: AiModeTab): void {
    this.activeMode.set(mode);
    this.ai.clearError();
  }

  protected setRoomTool(tool: RoomAiTool): void {
    this.activeRoomTool.set(tool);
    this.ai.clearError();
  }

  protected setIdeaTool(tool: IdeaAiTool): void {
    this.activeIdeaTool.set(tool);
    this.ai.clearError();
  }

  protected onClose(): void {
    this.closeDrawer.emit();
  }

  protected get currentIdea(): AiRoomIdea | undefined {
    return this.ideas.find(i => i.id === this.selectedIdeaId);
  }

  protected ideaText(i: AiRoomIdea): string {
    const main = (i.title || i.text || '').trim();
    const desc = (i.description || '').trim();
    if (main && desc) return `${main}: ${desc}`;
    return main || desc || 'Untitled';
  }

  // --- 1. Generate ---
  protected runGenerate(): void {
    const topic = this.generateTopic.trim();
    if (!topic || this.ai.loading()) return;

    this.generateResult.set(null);
    this.ai
      .generateIdeas({
        topic,
        context: this.generateContext.trim() || undefined,
        count: this.generateCount,
      })
      .subscribe({
        next: res => {
          this.generateResult.set(res);
        },
        error: err => this.handleError(err),
      });
  }

  protected onAddGeneratedIdea(item: { title: string; description: string }): void {
    this.addIdeaToCanvas.emit({
      title: item.title,
      description: item.description,
    });
    this.addedItems.update(prev => ({ ...prev, [item.title]: true }));
  }

  // --- 2. Group ---
  protected runGroup(): void {
    if (this.ideas.length < this.caps.groupMinIdeas || this.ai.loading()) return;

    const payload = this.ideas.slice(0, this.caps.groupMaxIdeas).map(i => ({
      id: i.id,
      text: this.ideaText(i),
    }));

    this.groupResult.set(null);
    this.ai.groupIdeas({ ideas: payload }).subscribe({
      next: res => {
        this.groupResult.set(res);
      },
      error: err => this.handleError(err),
    });
  }

  // --- 3. Summarize ---
  protected runSummarize(): void {
    if (this.ideas.length < this.caps.summarizeMinIdeas || this.ai.loading()) return;

    const payload = this.ideas.slice(0, this.caps.summarizeMaxIdeas).map(i => ({
      id: i.id,
      text: this.ideaText(i),
      votes: i.voteCount,
      category: i.category?.title,
    }));

    this.summarizeResult.set(null);
    this.ai
      .summarizeSession({
        sessionTitle: this.summarizeSessionTitle.trim() || undefined,
        ideas: payload,
      })
      .subscribe({
        next: res => {
          this.summarizeResult.set(res);
        },
        error: err => this.handleError(err),
      });
  }

  // --- 4. Compare ---
  protected toggleCompareIdea(id: string): void {
    const current = this.compareSelectedIds();
    if (current.includes(id)) {
      this.compareSelectedIds.set(current.filter(item => item !== id));
    } else {
      if (current.length < this.caps.compareMaxIdeas) {
        this.compareSelectedIds.set([...current, id]);
      }
    }
  }

  protected runCompare(): void {
    const selected = this.compareSelectedIds();
    if (
      selected.length < this.caps.compareMinIdeas ||
      selected.length > this.caps.compareMaxIdeas ||
      this.ai.loading()
    ) {
      return;
    }

    const payload = this.ideas
      .filter(i => selected.includes(i.id))
      .map(i => ({
        id: i.id,
        text: this.ideaText(i),
      }));

    this.compareResult.set(null);
    this.ai.compareIdeas({ ideas: payload }).subscribe({
      next: res => {
        this.compareResult.set(res);
      },
      error: err => this.handleError(err),
    });
  }

  protected getIdeaTitleById(id: string): string {
    const idea = this.ideas.find(i => i.id === id);
    return idea?.title || idea?.text || id;
  }

  // --- 5. Improve ---
  protected runImprove(): void {
    const idea = this.currentIdea;
    if (!idea || this.ai.loading()) return;

    this.improveResult.set(null);
    this.ai
      .improveIdea({
        idea: {
          id: idea.id,
          text: this.ideaText(idea),
        },
        mode: this.improveMode,
      })
      .subscribe({
        next: res => {
          this.improveResult.set(res);
        },
        error: err => this.handleError(err),
      });
  }

  protected onUpdateCurrentIdea(): void {
    const res = this.improveResult()?.result;
    const idea = this.currentIdea;
    if (!res || !idea) return;

    this.updateIdeaOnCanvas.emit({
      id: idea.id,
      title: res.title,
      description: res.text,
    });
    this.showCopied('Updated idea on canvas!');
  }

  protected onAddImprovedAsNew(): void {
    const res = this.improveResult()?.result;
    if (!res) return;

    this.addIdeaToCanvas.emit({
      title: res.title,
      description: res.text,
    });
    this.showCopied('Added as new idea on canvas!');
  }

  // --- 6. Action Plan ---
  protected runActionPlan(): void {
    const idea = this.currentIdea;
    if (!idea || this.ai.loading()) return;

    this.actionPlanResult.set(null);
    this.ai
      .actionPlan({
        goal: this.actionPlanGoal.trim() || undefined,
        ideas: [
          {
            id: idea.id,
            text: this.ideaText(idea),
          },
        ],
      })
      .subscribe({
        next: res => {
          this.actionPlanResult.set(res);
        },
        error: err => this.handleError(err),
      });
  }

  // --- 7. Risks ---
  protected runRisks(): void {
    const idea = this.currentIdea;
    if (!idea || this.ai.loading()) return;

    this.risksResult.set(null);
    this.ai
      .findRisks({
        idea: {
          id: idea.id,
          text: this.ideaText(idea),
        },
        context: this.risksContext.trim() || undefined,
      })
      .subscribe({
        next: res => {
          this.risksResult.set(res);
        },
        error: err => this.handleError(err),
      });
  }

  // Copy helper
  protected copyToClipboard(text: string, label = 'Copied!'): void {
    void navigator.clipboard.writeText(text);
    this.showCopied(label);
  }

  private showCopied(msg: string): void {
    this.copySuccess.set(msg);
    setTimeout(() => {
      if (this.copySuccess() === msg) {
        this.copySuccess.set(null);
      }
    }, 2500);
  }

  protected retryCurrentTool(): void {
    this.ai.clearError();
    if (this.activeMode() === 'room') {
      switch (this.activeRoomTool()) {
        case 'generate':
          this.runGenerate();
          break;
        case 'group':
          this.runGroup();
          break;
        case 'summarize':
          this.runSummarize();
          break;
        case 'compare':
          this.runCompare();
          break;
      }
    } else {
      switch (this.activeIdeaTool()) {
        case 'improve':
          this.runImprove();
          break;
        case 'action-plan':
          this.runActionPlan();
          break;
        case 'risks':
          this.runRisks();
          break;
      }
    }
  }

  private handleError(err: unknown): void {
    const error = this.ai.currentError();
    if (error && error.code === 'PROVIDER_BUSY' && error.retryAfterSeconds) {
      this.startCountdown(error.retryAfterSeconds);
    }
  }

  private startCountdown(seconds: number): void {
    this.stopCountdown();
    this.retryCountdown.set(seconds);
    this.countdownInterval = setInterval(() => {
      const remaining = this.retryCountdown() - 1;
      if (remaining <= 0) {
        this.stopCountdown();
      } else {
        this.retryCountdown.set(remaining);
      }
    }, 1000);
  }

  private stopCountdown(): void {
    if (this.countdownInterval) {
      clearInterval(this.countdownInterval);
      this.countdownInterval = undefined;
    }
    this.retryCountdown.set(0);
  }
}
