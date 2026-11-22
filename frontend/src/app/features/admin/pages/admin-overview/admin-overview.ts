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
      font: 700 1.6rem Georgia, serif;
      color: #233b3b;
      margin: 0 0 6px;
    }
    .subtitle {
      color: #60736d;
      font-size: 0.9rem;
      margin: 0;
    }
    .refresh-btn {
      padding: 8px 16px;
      background: #ffffff;
      border: 1px solid #d9ddd5;
      border-radius: 6px;
      color: #233b3b;
      font: 500 0.85rem inherit;
      cursor: pointer;
      white-space: nowrap;
    }
    .refresh-btn:hover:not(:disabled) {
      border-color: #233b3b;
      background: #fbfaf8;
    }
    .service-alert {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 10px 16px;
      border-radius: 6px;
      margin-bottom: 16px;
      font-size: 0.88rem;
    }
    .service-alert.warning {
      background: #fff8e6;
      border: 1px solid #f9df9b;
      color: #8c5a00;
    }
    .service-alert button {
      background: #8c5a00;
      color: #fff;
      border: none;
      padding: 4px 10px;
      border-radius: 4px;
      cursor: pointer;
    }
    .metric-section {
      margin-bottom: 28px;
    }
    .section-title {
      font: 700 1.05rem Georgia, serif;
      color: #233b3b;
      margin: 0 0 12px;
    }
    .kpi-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 16px;
    }
    .kpi-card {
      background: #ffffff;
      border: 1px solid #e5e2dc;
      border-radius: 10px;
      padding: 20px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
    }
    .kpi-label {
      font-size: 0.78rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: #6b7280;
      margin-bottom: 8px;
    }
    .kpi-value {
      font: 700 1.8rem 'DM Mono', monospace;
      color: #233b3b;
      margin-bottom: 6px;
    }
    .kpi-value.highlight {
      color: #e9784d;
    }
    .kpi-value.success {
      color: #0d9488;
    }
    .kpi-value.purple {
      color: #7c3aed;
    }
    .kpi-sub {
      font-size: 0.78rem;
      color: #8b9592;
    }
    .details-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 20px;
    }
    .detail-card {
      background: #ffffff;
      border: 1px solid #e5e2dc;
      border-radius: 10px;
      padding: 24px;
    }
    .detail-card h3 {
      font: 700 1.05rem Georgia, serif;
      color: #233b3b;
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
      margin-bottom: 4px;
    }
    .progress-track {
      height: 8px;
      background: #f0ede8;
      border-radius: 4px;
      overflow: hidden;
    }
    .progress-fill {
      height: 100%;
      background: #0d9488;
      border-radius: 4px;
      transition: width 0.3s ease;
    }
    .recent-events-list {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    .event-item {
      padding: 8px 12px;
      background: #faf8f5;
      border-radius: 6px;
      border: 1px solid #f0ede8;
    }
    .event-main {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 0.86rem;
      font-weight: 600;
      margin-bottom: 2px;
    }
    .event-type {
      font-size: 0.72rem;
      padding: 2px 6px;
      border-radius: 4px;
      background: #e5e7eb;
      color: #374151;
    }
    .event-type.subscribe { background: #dcfce7; color: #15803d; }
    .event-type.upgrade { background: #dbeafe; color: #1e40af; }
    .event-type.cancel, .event-type.admin_cancel { background: #fee2e2; color: #b91c1c; }
    .event-type.admin_assign { background: #ede9fe; color: #6d28d9; }
    .event-amount {
      margin-left: auto;
      font-family: 'DM Mono', monospace;
    }
    .event-meta {
      display: flex;
      justify-content: space-between;
      font-size: 0.75rem;
      color: #9ca3af;
    }
    .empty-text {
      color: #9ca3af;
      font-size: 0.85rem;
      font-style: italic;
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
