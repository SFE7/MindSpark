import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { AdminService } from '../../../../core/admin/admin.service';

@Component({
  selector: 'app-admin-overview',
  standalone: true,
  imports: [CommonModule, CurrencyPipe, DatePipe],
  template: `
    <div class="overview-page">
      <header class="page-header">
        <div>
          <h1>Platform Overview &amp; Statistics</h1>
          <p class="subtitle">Real-time health, adoption metrics, billing volume, and AI utilization.</p>
        </div>
        <button class="refresh-btn" (click)="refresh()" [disabled]="adminService.overviewLoading()">
          {{ adminService.overviewLoading() ? 'Refreshing...' : '🔄 Refresh Stats' }}
        </button>
      </header>

      <!-- Localized Resilient Error Banners -->
      @if (adminService.platformError(); as pErr) {
        <div class="service-alert warning">
          <span>⚠️ Platform Metrics Unavailable: {{ pErr }}</span>
          <button (click)="refresh()">Retry</button>
        </div>
      }
      @if (adminService.billingError(); as bErr) {
        <div class="service-alert warning">
          <span>⚠️ Billing Metrics Unavailable: {{ bErr }}</span>
          <button (click)="refresh()">Retry</button>
        </div>
      }
      @if (adminService.aiError(); as aiErr) {
        <div class="service-alert warning">
          <span>⚠️ AI Metrics Unavailable: {{ aiErr }}</span>
          <button (click)="refresh()">Retry</button>
        </div>
      }

      <!-- KPI Grid: Collaboration & Rooms -->
      <section class="metric-section">
        <h2 class="section-title">Collaboration Activity</h2>
        <div class="kpi-grid">
          <div class="kpi-card">
            <div class="kpi-label">Total Users</div>
            <div class="kpi-value">{{ (adminService.platformStats()?.totalUsers ?? 0) | number }}</div>
            <div class="kpi-sub">Registered platform accounts</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Active Rooms</div>
            <div class="kpi-value highlight">{{ (adminService.platformStats()?.activeRooms ?? 0) | number }}</div>
            <div class="kpi-sub">Of {{ (adminService.platformStats()?.totalRooms ?? 0) | number }} total rooms ({{ (adminService.platformStats()?.closedRooms ?? 0) | number }} closed)</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Total Ideas</div>
            <div class="kpi-value">{{ (adminService.platformStats()?.totalIdeas ?? 0) | number }}</div>
            <div class="kpi-sub">Brainstormed sticky notes</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Total Votes</div>
            <div class="kpi-value">{{ (adminService.platformStats()?.totalVotes ?? 0) | number }}</div>
            <div class="kpi-sub">Idea votes cast across rooms</div>
          </div>
        </div>
      </section>

      <!-- KPI Grid: Revenue & Subscriptions -->
      <section class="metric-section">
        <h2 class="section-title">Revenue &amp; Subscriptions</h2>
        <div class="kpi-grid">
          <div class="kpi-card">
            <div class="kpi-label">Active Paid Subscribers</div>
            <div class="kpi-value success">{{ (adminService.billingStats()?.activePaidSubscriptionsCount ?? 0) | number }}</div>
            <div class="kpi-sub">Active paid subscriptions</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Estimated MRR</div>
            <div class="kpi-value success">
              {{ ((adminService.billingStats()?.estimatedMrrCents ?? 0) / 100) | currency:'USD':'symbol':'1.0-2' }}
            </div>
            <div class="kpi-sub">Monthly Recurring Revenue</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Total Revenue Collected</div>
            <div class="kpi-value">
              {{ ((adminService.billingStats()?.totalRevenueCents ?? 0) / 100) | currency:'USD':'symbol':'1.0-2' }}
            </div>
            <div class="kpi-sub">Settled invoices all time</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">30-Day Billing Events</div>
            <div class="kpi-value">{{ (adminService.billingStats()?.recentBillingEventsCount30d ?? 0) | number }}</div>
            <div class="kpi-sub">Checkouts, upgrades &amp; renewals</div>
          </div>
        </div>
      </section>

      <!-- KPI Grid: AI Utilization -->
      <section class="metric-section">
        <h2 class="section-title">AI Utilization</h2>
        <div class="kpi-grid">
          <div class="kpi-card">
            <div class="kpi-label">AI Calls Today (UTC)</div>
            <div class="kpi-value purple">{{ (adminService.aiStats()?.callsTodayUtc ?? 0) | number }}</div>
            <div class="kpi-sub">Total prompt &amp; generation calls</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Active AI Users Today</div>
            <div class="kpi-value purple">{{ (adminService.aiStats()?.activeAiUsersToday ?? 0) | number }}</div>
            <div class="kpi-sub">Unique users calling AI today</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">AI Calls All-Time</div>
            <div class="kpi-value">{{ (adminService.aiStats()?.totalCallsAllTime ?? 0) | number }}</div>
            <div class="kpi-sub">Cumulative lifetime AI calls</div>
          </div>
        </div>
      </section>

      <!-- Plan Distribution & Recent Billing Events side-by-side -->
      <div class="details-grid">
        <div class="card detail-card">
          <h3>Subscribers by Plan</h3>
          <div class="plan-bars">
            @for (p of adminService.billingStats()?.subscribersByPlan ?? []; track p.planCode) {
              <div class="plan-bar-item">
                <div class="plan-bar-header">
                  <span class="plan-code">{{ p.planName }} ({{ p.planCode }})</span>
                  <span class="plan-count">{{ p.activeCount }} active</span>
                </div>
                <div class="progress-track">
                  <div class="progress-fill" [style.width.%]="calcPercentage(p.activeCount)"></div>
                </div>
              </div>
            } @empty {
              <p class="empty-text">No active paid subscriptions found.</p>
            }
          </div>
        </div>

        <div class="card detail-card">
          <h3>Recent Billing Events</h3>
          <div class="recent-events-list">
            @for (b of adminService.billingStats()?.recentBillingRecords ?? []; track b.id) {
              <div class="event-item">
                <div class="event-main">
                  <span class="event-type" [ngClass]="b.eventType.toLowerCase()">{{ b.eventType }}</span>
                  <span class="event-plan">{{ b.planName }}</span>
                  <span class="event-amount">{{ (b.amountCents / 100) | currency:b.currency:'symbol':'1.0-2' }}</span>
                </div>
                <div class="event-meta">
                  <span class="event-invoice">{{ b.invoiceNumber }}</span>
                  <span class="event-date">{{ b.createdAt | date:'short' }}</span>
                </div>
              </div>
            } @empty {
              <p class="empty-text">No recent billing events.</p>
            }
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .overview-page {
      max-width: 1200px;
      margin: 0 auto;
    }
    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 24px;
      gap: 16px;
    }
    h1 {
      font-family: var(--ms-font-display, 'Bricolage Grotesque', sans-serif);
      font-size: 1.7rem;
      font-weight: 800;
      color: var(--ms-text, #F4F6FF);
      margin: 0 0 6px;
    }
    .subtitle {
      color: var(--ms-text-muted, #9AA5CE);
      font-size: 0.9rem;
      margin: 0;
    }
    .refresh-btn {
      padding: 9px 16px;
      background: var(--ms-surface, #0D1438);
      border: 1px solid var(--ms-border, #27316E);
      border-radius: 10px;
      color: var(--ms-text-secondary, #C5CEEE);
      font-size: 0.85rem;
      font-weight: 600;
      cursor: pointer;
      white-space: nowrap;
    }
    .refresh-btn:hover:not(:disabled) {
      border-color: var(--ms-primary, #6F82F2);
      color: #FFFFFF;
    }
    .service-alert {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 11px 16px;
      border-radius: 12px;
      margin-bottom: 16px;
      font-size: 0.88rem;
    }
    .service-alert.warning {
      background: var(--ms-warning-bg);
      border: 1px solid var(--ms-warning-border);
      color: var(--ms-gold, #FFC83D);
    }
    .service-alert button {
      background: var(--ms-gold, #FFC83D);
      color: #060A22;
      border: none;
      padding: 4px 12px;
      border-radius: 8px;
      font-weight: 700;
      cursor: pointer;
    }
    .metric-section {
      margin-bottom: 28px;
    }
    .section-title {
      font-family: var(--ms-font-display, 'Bricolage Grotesque', sans-serif);
      font-size: 1.1rem;
      font-weight: 700;
      color: var(--ms-text, #F4F6FF);
      margin: 0 0 12px;
    }
    .kpi-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 16px;
    }
    .kpi-card {
      background: var(--ms-surface, #0D1438);
      border: 1px solid var(--ms-border, #27316E);
      border-radius: 16px;
      padding: 20px;
      box-shadow: var(--ms-shadow-card);
    }
    .kpi-label {
      font-family: var(--ms-font-mono, monospace);
      font-size: 0.74rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--ms-text-muted, #9AA5CE);
      margin-bottom: 8px;
    }
    .kpi-value {
      font-family: var(--ms-font-mono, monospace);
      font-size: 1.85rem;
      font-weight: 700;
      color: var(--ms-text, #F4F6FF);
      margin-bottom: 6px;
    }
    .kpi-value.highlight {
      color: var(--ms-gold, #FFC83D);
    }
    .kpi-value.success {
      color: var(--ms-mint, #34D399);
    }
    .kpi-value.purple {
      color: var(--ms-violet, #8B5CF6);
    }
    .kpi-sub {
      font-size: 0.78rem;
      color: var(--ms-text-muted, #9AA5CE);
    }
    .details-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 20px;
    }
    .detail-card {
      background: var(--ms-surface, #0D1438);
      border: 1px solid var(--ms-border, #27316E);
      border-radius: 16px;
      padding: 24px;
    }
    .detail-card h3 {
      font-family: var(--ms-font-display, 'Bricolage Grotesque', sans-serif);
      font-size: 1.08rem;
      font-weight: 700;
      color: var(--ms-text, #F4F6FF);
      margin: 0 0 16px;
    }
    .plan-bars {
      display: flex;
      flex-direction: column;
      gap: 14px;
    }
    .plan-bar-header {
      display: flex;
      justify-content: space-between;
      font-size: 0.85rem;
      font-weight: 600;
      color: var(--ms-text-secondary, #C5CEEE);
      margin-bottom: 6px;
    }
    .progress-track {
      height: 8px;
      background: var(--ms-surface-raised, #141D4F);
      border-radius: 999px;
      overflow: hidden;
    }
    .progress-fill {
      height: 100%;
      background: linear-gradient(90deg, var(--ms-primary, #6F82F2), var(--ms-mint, #34D399));
      border-radius: 999px;
      transition: width 0.3s ease;
    }
    .recent-events-list {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    .event-item {
      padding: 10px 14px;
      background: var(--ms-surface-raised, #141D4F);
      border-radius: 12px;
      border: 1px solid var(--ms-border, #27316E);
    }
    .event-main {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 0.86rem;
      font-weight: 600;
      margin-bottom: 4px;
    }
    .event-type {
      font-family: var(--ms-font-mono, monospace);
      font-size: 0.7rem;
      padding: 2px 7px;
      border-radius: 6px;
      background: var(--ms-surface, #0D1438);
      color: var(--ms-text-secondary, #C5CEEE);
    }
    .event-type.subscribe { background: var(--ms-success-bg); color: var(--ms-mint, #34D399); }
    .event-type.upgrade { background: var(--ms-info-bg); color: var(--ms-info, #38BDF8); }
    .event-type.cancel, .event-type.admin_cancel { background: var(--ms-error-bg); color: var(--ms-coral, #FF9A8F); }
    .event-type.admin_assign { background: rgba(139, 92, 246, 0.2); color: #C4B5FD; }
    .event-amount {
      margin-left: auto;
      font-family: var(--ms-font-mono, monospace);
      color: var(--ms-gold, #FFC83D);
    }
    .event-meta {
      display: flex;
      justify-content: space-between;
      font-size: 0.75rem;
      color: var(--ms-text-muted, #9AA5CE);
    }
    .empty-text {
      color: var(--ms-text-muted, #9AA5CE);
      font-size: 0.85rem;
    }
    @media (max-width: 860px) {
      .details-grid {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class AdminOverviewComponent implements OnInit {
  protected readonly adminService = inject(AdminService);

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.adminService.loadOverviewStats().subscribe();
  }

  calcPercentage(count: number): number {
    const total = this.adminService.billingStats()?.activePaidSubscriptionsCount ?? 0;
    if (total <= 0) return 0;
    return Math.min(100, Math.round((count / total) * 100));
  }
}
