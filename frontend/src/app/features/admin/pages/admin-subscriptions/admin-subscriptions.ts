import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../../../core/admin/admin.service';
import { AdminAssignRequest, AdminPlan, AdminSubscription, AdminUserDirectory } from '../../../../core/admin/admin.models';

@Component({
  selector: 'app-admin-subscriptions',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe],
  template: `
    <div class="subs-page">
      <header class="page-header">
        <div>
          <h1>Subscription Management</h1>
          <p class="subtitle">Search, filter, override plans, and cancel user subscriptions. (Story 62)</p>
        </div>
        <button class="primary-btn" (click)="openAssignModal()">+ Assign / Override Plan</button>
      </header>

      @if (actionError) {
        <div class="alert danger">
          <span>{{ actionError }}</span>
          <button (click)="actionError = null">✕</button>
        </div>
      }
      @if (actionSuccess) {
        <div class="alert success">
          <span>{{ actionSuccess }}</span>
          <button (click)="actionSuccess = null">✕</button>
        </div>
      }

      <!-- Search & Filter Bar -->
      <div class="filter-card">
        <div class="filter-group">
          <label>Filter by Status</label>
          <select [(ngModel)]="filterStatus" (change)="onFilterChange()">
            <option value="">All Statuses</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="CANCELED">CANCELED</option>
            <option value="EXPIRED">EXPIRED</option>
          </select>
        </div>

        <div class="filter-group">
          <label>Filter by Plan</label>
          <select [(ngModel)]="filterPlan" (change)="onFilterChange()">
            <option value="">All Plans</option>
            @for (p of adminService.plans(); track p.id) {
              <option [value]="p.code">{{ p.name }} ({{ p.code }})</option>
            }
          </select>
        </div>

        <div class="filter-group search-group">
          <label>Filter by User ID / Keycloak Sub</label>
          <input type="text" [(ngModel)]="filterUserId" (keyup.enter)="onFilterChange()" placeholder="Enter user ID...">
        </div>

        <button class="filter-btn" (click)="onFilterChange()">Apply</button>
        <button class="clear-btn" (click)="clearFilters()">Reset</button>
      </div>

      <!-- Subscriptions Table -->
      <div class="table-container">
        <table class="subs-table">
          <thead>
            <tr>
              <th>User</th>
              <th>Plan</th>
              <th>Status</th>
              <th>Started</th>
              <th>Current Period</th>
              <th>Canceled At</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            @for (sub of adminService.subscriptionsPage()?.items ?? []; track sub.id) {
              <tr>
                <td>
                  <div class="user-cell">
                    <span class="user-name">{{ resolveUserName(sub.userId) }}</span>
                    <span class="user-sub" [title]="sub.userId">{{ resolveUserEmail(sub.userId) || sub.userId }}</span>
                  </div>
                </td>
                <td>
                  <span class="plan-pill-tag">{{ sub.planName }} ({{ sub.planCode }})</span>
                </td>
                <td>
                  <span class="status-pill" [ngClass]="sub.status.toLowerCase()">{{ sub.status }}</span>
                </td>
                <td class="date-col">{{ sub.startedAt | date:'mediumDate' }}</td>
                <td class="date-col">
                  {{ sub.currentPeriodStart | date:'shortDate' }} – {{ sub.currentPeriodEnd | date:'shortDate' }}
                </td>
                <td class="date-col">
                  {{ sub.canceledAt ? (sub.canceledAt | date:'shortDate') : '—' }}
                </td>
                <td class="actions-col">
                  @if (sub.status === 'ACTIVE') {
                    <button class="action-btn cancel-btn" (click)="openCancelModal(sub)">Cancel</button>
                  }
                  <button class="action-btn change-btn" (click)="openAssignModalForUser(sub.userId)">Change Plan</button>
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="7" class="empty-cell">
                  @if (adminService.subscriptionsLoading()) {
                    Loading subscriptions...
                  } @else {
                    No subscriptions match your filter criteria.
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>

        <!-- Pagination Bar -->
        @if (adminService.subscriptionsPage(); as pageData) {
          @if (pageData.totalPages > 1) {
            <div class="pagination-bar">
              <span class="page-info">
                Page {{ pageData.page + 1 }} of {{ pageData.totalPages }} ({{ pageData.totalElements }} total records)
              </span>
              <div class="page-controls">
                <button [disabled]="pageData.page === 0" (click)="goToPage(pageData.page - 1)">Previous</button>
                <button [disabled]="pageData.page >= pageData.totalPages - 1" (click)="goToPage(pageData.page + 1)">Next</button>
              </div>
            </div>
          }
        }
      </div>

      <!-- Change / Assign Plan Modal -->
      @if (showAssignModal) {
        <div class="modal-backdrop">
          <div class="modal-card">
            <h2>Assign or Override Subscription Plan</h2>
            <p class="modal-desc">
              Directly assign or switch a user's subscription tier. If selecting FREE, any active paid subscription will be cancelled and premium role revoked.
            </p>

            <form (ngSubmit)="submitAssign()">
              <div class="form-group">
                <label>Select User from Directory *</label>
                <select [(ngModel)]="assignUserId" name="assignUserId" required>
                  <option value="">-- Choose registered user --</option>
                  @for (u of adminService.usersDirectory(); track u.id) {
                    <option [value]="u.keycloakId">{{ u.fullName }} ({{ u.email }})</option>
                  }
                </select>
              </div>

              <div class="form-group">
                <label>Or manually enter Keycloak Subject ID (sub)</label>
                <input type="text" [(ngModel)]="manualUserId" name="manualUserId" placeholder="e.g. 550e8400-e29b-41d4-a716-446655440000">
              </div>

              <div class="form-group">
                <label>Target Plan *</label>
                <select [(ngModel)]="assignPlanCode" name="assignPlanCode" required>
                  @for (p of adminService.plans(); track p.id) {
                    <option [value]="p.code">{{ p.name }} ({{ p.code }}) – {{ (p.priceCents / 100) | currency:p.currency }}</option>
                  }
                </select>
              </div>

              <div class="form-group">
                <label>Override Reason (logged in invoice record)</label>
                <input type="text" [(ngModel)]="assignReason" name="assignReason" placeholder="e.g. Executive comp / Support ticket #412">
              </div>

              <div class="modal-actions">
                <button type="button" class="btn-secondary" (click)="showAssignModal = false">Cancel</button>
                <button type="submit" class="btn-primary" [disabled]="submitting">
                  {{ submitting ? 'Applying...' : 'Apply Plan Override' }}
                </button>
              </div>
            </form>
          </div>
        </div>
      }

      <!-- Cancel Confirmation Modal -->
      @if (showCancelModal && targetSubToCancel) {
        <div class="modal-backdrop">
          <div class="modal-card cancel-modal">
            <h2>Cancel User Subscription</h2>
            <p class="modal-desc">
              Are you sure you want to cancel the active subscription for
              <strong>{{ resolveUserName(targetSubToCancel.userId) }}</strong> (Plan: {{ targetSubToCancel.planName }})?
            </p>
            <p class="warning-text">
              The user will immediately lose access to premium AI capabilities, and their Keycloak <code>premium</code> role will be revoked.
            </p>

            <div class="modal-actions">
              <button type="button" class="btn-secondary" (click)="showCancelModal = false">No, Keep Subscription</button>
              <button type="button" class="btn-danger" (click)="confirmCancel()" [disabled]="submitting">
                {{ submitting ? 'Canceling...' : 'Yes, Cancel Subscription' }}
              </button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .subs-page {
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
    .primary-btn {
      padding: 10px 18px;
      background: var(--ms-primary, #6F82F2);
      color: #FFFFFF;
      border: 1px solid rgba(180, 190, 255, 0.4);
      border-radius: 12px;
      font-size: 0.88rem;
      font-weight: 700;
      cursor: pointer;
      transition: all 0.16s ease;
    }
    .primary-btn:hover {
      background: var(--ms-primary-hover, #8294F8);
      box-shadow: var(--ms-glow-primary);
    }
    .filter-card {
      display: flex;
      gap: 14px;
      align-items: flex-end;
      background: var(--ms-surface, #0D1438);
      padding: 16px 20px;
      border: 1px solid var(--ms-border, #27316E);
      border-radius: 16px;
      margin-bottom: 20px;
      flex-wrap: wrap;
    }
    .filter-group {
      display: flex;
      flex-direction: column;
      gap: 5px;
    }
    .filter-group label {
      font-family: var(--ms-font-mono, monospace);
      font-size: 0.72rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: var(--ms-text-muted, #9AA5CE);
    }
    .filter-group select, .filter-group input {
      padding: 8px 12px;
      border: 1px solid var(--ms-border, #27316E);
      border-radius: 10px;
      background: var(--ms-surface-raised, #141D4F);
      color: var(--ms-text, #F4F6FF);
      font-size: 0.86rem;
      min-width: 160px;
      outline: none;
    }
    .filter-group select:focus, .filter-group input:focus {
      border-color: var(--ms-primary, #6F82F2);
    }
    .search-group input {
      min-width: 260px;
    }
    .filter-btn {
      padding: 8px 16px;
      background: var(--ms-primary, #6F82F2);
      color: #FFFFFF;
      border: none;
      border-radius: 10px;
      font-size: 0.86rem;
      font-weight: 700;
      cursor: pointer;
    }
    .clear-btn {
      padding: 8px 14px;
      background: transparent;
      border: 1px solid var(--ms-border, #27316E);
      color: var(--ms-text-muted, #9AA5CE);
      border-radius: 10px;
      font-size: 0.86rem;
      cursor: pointer;
    }
    .table-container {
      background: var(--ms-surface, #0D1438);
      border: 1px solid var(--ms-border, #27316E);
      border-radius: 16px;
      overflow-x: auto;
      box-shadow: var(--ms-shadow-card);
    }
    .subs-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.88rem;
      text-align: left;
    }
    .subs-table th {
      background: var(--ms-surface-raised, #141D4F);
      padding: 13px 16px;
      font-family: var(--ms-font-mono, monospace);
      font-weight: 700;
      color: var(--ms-text-muted, #9AA5CE);
      border-bottom: 1px solid var(--ms-border, #27316E);
      font-size: 0.75rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    .subs-table td {
      padding: 14px 16px;
      border-bottom: 1px solid var(--ms-border-subtle);
      vertical-align: middle;
      color: var(--ms-text-secondary, #C5CEEE);
    }
    .user-cell {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .user-name {
      font-weight: 700;
      color: var(--ms-text, #F4F6FF);
    }
    .user-sub {
      font-size: 0.75rem;
      color: var(--ms-text-muted, #9AA5CE);
      max-width: 220px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .plan-pill-tag {
      font-family: var(--ms-font-mono, monospace);
      font-size: 0.78rem;
      font-weight: 600;
      background: var(--ms-surface-raised, #141D4F);
      border: 1px solid var(--ms-border, #27316E);
      padding: 3px 8px;
      border-radius: 6px;
      color: var(--ms-primary-light, #B4BEFF);
    }
    .status-pill {
      display: inline-block;
      font-size: 0.74rem;
      font-weight: 700;
      padding: 3px 9px;
      border-radius: 999px;
    }
    .status-pill.active { background: var(--ms-success-bg); color: var(--ms-mint, #34D399); }
    .status-pill.canceled { background: var(--ms-error-bg); color: var(--ms-coral, #FF9A8F); }
    .status-pill.expired { background: var(--ms-surface-raised, #141D4F); color: var(--ms-text-muted, #9AA5CE); }
    .date-col {
      font-family: var(--ms-font-mono, monospace);
      font-size: 0.8rem;
      color: var(--ms-text-muted, #9AA5CE);
    }
    .actions-col {
      display: flex;
      gap: 6px;
    }
    .action-btn {
      padding: 5px 11px;
      border-radius: 8px;
      font-size: 0.78rem;
      font-weight: 600;
      cursor: pointer;
      border: 1px solid var(--ms-border, #27316E);
      background: var(--ms-surface-raised, #141D4F);
      color: var(--ms-text-secondary, #C5CEEE);
    }
    .cancel-btn:hover { background: var(--ms-error-bg); border-color: var(--ms-error-border); color: var(--ms-coral, #FF9A8F); }
    .change-btn:hover { border-color: var(--ms-primary, #6F82F2); color: #FFFFFF; }
    .pagination-bar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 14px 20px;
      background: var(--ms-surface-raised, #141D4F);
      border-top: 1px solid var(--ms-border, #27316E);
      font-size: 0.82rem;
      color: var(--ms-text-muted, #9AA5CE);
    }
    .page-controls button {
      padding: 5px 12px;
      margin-left: 8px;
      border: 1px solid var(--ms-border, #27316E);
      border-radius: 8px;
      background: var(--ms-surface, #0D1438);
      color: var(--ms-text, #F4F6FF);
      cursor: pointer;
    }
    .page-controls button:disabled {
      opacity: 0.45;
      cursor: not-allowed;
    }
    .alert {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 11px 16px;
      border-radius: 12px;
      margin-bottom: 16px;
      font-size: 0.88rem;
    }
    .alert.danger { background: var(--ms-error-bg); border: 1px solid var(--ms-error-border); color: var(--ms-coral, #FF9A8F); }
    .alert.success { background: var(--ms-success-bg); border: 1px solid var(--ms-success-border); color: var(--ms-mint, #34D399); }
    .alert button { background: none; border: none; color: inherit; cursor: pointer; }
    .modal-backdrop {
      position: fixed;
      inset: 0;
      background: var(--ms-modal-backdrop, rgba(4, 7, 24, 0.75));
      backdrop-filter: blur(8px);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 999;
      padding: 20px;
    }
    .modal-card {
      background: var(--ms-surface, #0D1438);
      border: 1px solid var(--ms-border-glass);
      border-radius: 18px;
      width: 100%;
      max-width: 520px;
      padding: 30px;
      box-shadow: var(--ms-shadow-glass);
      color: var(--ms-text, #F4F6FF);
    }
    .modal-card h2 {
      font-family: var(--ms-font-display, 'Bricolage Grotesque', sans-serif);
      font-size: 1.3rem;
      font-weight: 700;
      color: var(--ms-text, #F4F6FF);
      margin: 0 0 8px;
    }
    .modal-desc {
      color: var(--ms-text-muted, #9AA5CE);
      font-size: 0.86rem;
      margin: 0 0 20px;
      line-height: 1.45;
    }
    .warning-text {
      color: var(--ms-coral, #FF9A8F);
      font-size: 0.84rem;
      background: var(--ms-error-bg);
      border: 1px solid var(--ms-error-border);
      padding: 10px 14px;
      border-radius: 10px;
      margin-bottom: 20px;
    }
    .form-group {
      display: flex;
      flex-direction: column;
      gap: 6px;
      margin-bottom: 14px;
    }
    .form-group label {
      font-family: var(--ms-font-mono, monospace);
      font-size: 0.74rem;
      font-weight: 700;
      color: var(--ms-text-muted, #9AA5CE);
      text-transform: uppercase;
    }
    .form-group input, .form-group select {
      padding: 9px 12px;
      border: 1px solid var(--ms-border, #27316E);
      border-radius: 10px;
      background: var(--ms-surface-raised, #141D4F);
      color: var(--ms-text, #F4F6FF);
      font-size: 0.88rem;
      outline: none;
    }
    .modal-actions {
      display: flex;
      justify-content: flex-end;
      gap: 12px;
      margin-top: 24px;
      padding-top: 16px;
      border-top: 1px solid var(--ms-border-subtle);
    }
    .btn-secondary {
      padding: 9px 16px;
      border: 1px solid var(--ms-border, #27316E);
      background: transparent;
      color: var(--ms-text-muted, #9AA5CE);
      border-radius: 10px;
      cursor: pointer;
    }
    .btn-primary {
      padding: 9px 18px;
      background: var(--ms-primary, #6F82F2);
      color: #FFFFFF;
      border: none;
      border-radius: 10px;
      font-weight: 700;
      cursor: pointer;
    }
    .btn-primary:hover:not(:disabled) { background: var(--ms-primary-hover, #8294F8); }
    .btn-danger {
      padding: 9px 18px;
      background: var(--ms-error, #F43F5E);
      color: #FFFFFF;
      border: none;
      border-radius: 10px;
      font-weight: 700;
      cursor: pointer;
    }
    .empty-cell { text-align: center; padding: 36px; color: var(--ms-text-muted, #9AA5CE); }
  `]
})
export class AdminSubscriptionsComponent implements OnInit {
  protected readonly adminService = inject(AdminService);

  filterStatus = '';
  filterPlan = '';
  filterUserId = '';
  currentPage = 0;

  showAssignModal = false;
  showCancelModal = false;
  targetSubToCancel: AdminSubscription | null = null;
  submitting = false;

  assignUserId = '';
  manualUserId = '';
  assignPlanCode = 'PREMIUM';
  assignReason = '';

  actionError: string | null = null;
  actionSuccess: string | null = null;

  ngOnInit(): void {
    this.adminService.loadPlans().subscribe();
    this.adminService.loadUsersDirectory().subscribe();
    this.loadSubs();
  }

  loadSubs(page = this.currentPage): void {
    this.currentPage = page;
    this.adminService.loadSubscriptions({
      status: this.filterStatus || undefined,
      planCode: this.filterPlan || undefined,
      userId: this.filterUserId.trim() || undefined,
      page: this.currentPage,
      size: 20
    }).subscribe();
  }

  onFilterChange(): void {
    this.currentPage = 0;
    this.loadSubs(0);
  }

  clearFilters(): void {
    this.filterStatus = '';
    this.filterPlan = '';
    this.filterUserId = '';
    this.currentPage = 0;
    this.loadSubs(0);
  }

  goToPage(page: number): void {
    this.loadSubs(page);
  }

  resolveUserName(userId: string): string {
    const user = this.adminService.usersDirectory().find(u => u.keycloakId === userId);
    return user ? user.fullName : (userId.length > 16 ? userId.substring(0, 16) + '...' : userId);
  }

  resolveUserEmail(userId: string): string | null {
    const user = this.adminService.usersDirectory().find(u => u.keycloakId === userId);
    return user ? user.email : null;
  }

  openAssignModal(): void {
    this.actionError = null;
    this.actionSuccess = null;
    this.assignUserId = '';
    this.manualUserId = '';
    this.assignPlanCode = 'PREMIUM';
    this.assignReason = '';
    this.showAssignModal = true;
  }

  openAssignModalForUser(userId: string): void {
    this.openAssignModal();
    const user = this.adminService.usersDirectory().find(u => u.keycloakId === userId);
    if (user) {
      this.assignUserId = user.keycloakId;
    } else {
      this.manualUserId = userId;
    }
  }

  submitAssign(): void {
    const targetUserId = this.manualUserId.trim() || this.assignUserId.trim();
    if (!targetUserId) {
      this.actionError = 'Please select a user from the directory or enter a user ID.';
      return;
    }

    this.submitting = true;
    this.actionError = null;

    const req: AdminAssignRequest = {
      userId: targetUserId,
      planCode: this.assignPlanCode,
      reason: this.assignReason.trim() || undefined,
    };

    this.adminService.assignSubscription(req).subscribe({
      next: (sub) => {
        this.submitting = false;
        this.showAssignModal = false;
        this.actionSuccess = `Successfully set plan ${sub.planName} (${sub.planCode}) for user ${this.resolveUserName(targetUserId)}.`;
        this.loadSubs();
      },
      error: (err) => {
        this.submitting = false;
        this.actionError = err?.error?.message || 'Failed to assign plan.';
      }
    });
  }

  openCancelModal(sub: AdminSubscription): void {
    this.actionError = null;
    this.actionSuccess = null;
    this.targetSubToCancel = sub;
    this.showCancelModal = true;
  }

  confirmCancel(): void {
    if (!this.targetSubToCancel) return;
    this.submitting = true;
    this.actionError = null;

    this.adminService.cancelSubscription(this.targetSubToCancel.userId).subscribe({
      next: (res) => {
        this.submitting = false;
        this.showCancelModal = false;
        this.actionSuccess = `Subscription canceled successfully. Keycloak role sync status: ${res.roleSyncStatus}.`;
        this.loadSubs();
      },
      error: (err) => {
        this.submitting = false;
        this.actionError = err?.error?.message || 'Failed to cancel subscription.';
      }
    });
  }
}
