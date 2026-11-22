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
    .primary-btn {
      padding: 9px 18px;
      background: #233b3b;
      color: #fffaf1;
      border: none;
      border-radius: 6px;
      font: 600 0.88rem inherit;
      cursor: pointer;
      transition: background 0.15s ease;
    }
    .primary-btn:hover {
      background: #e9784d;
    }
    .filter-card {
      display: flex;
      gap: 14px;
      align-items: flex-end;
      background: #ffffff;
      padding: 16px 20px;
      border: 1px solid #e5e2dc;
      border-radius: 8px;
      margin-bottom: 20px;
      flex-wrap: wrap;
    }
    .filter-group {
      display: flex;
      flex-direction: column;
      gap: 5px;
    }
    .filter-group label {
      font-size: 0.76rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.03em;
      color: #6b7280;
    }
    .filter-group select, .filter-group input {
      padding: 7px 12px;
      border: 1px solid #d1d5db;
      border-radius: 6px;
      font-size: 0.86rem;
      min-width: 160px;
    }
    .search-group input {
      min-width: 260px;
    }
    .filter-btn {
      padding: 7px 16px;
      background: #233b3b;
      color: #fff;
      border: none;
      border-radius: 6px;
      font-size: 0.86rem;
      cursor: pointer;
    }
    .clear-btn {
      padding: 7px 14px;
      background: transparent;
      border: 1px solid #d1d5db;
      border-radius: 6px;
      font-size: 0.86rem;
      cursor: pointer;
    }
    .table-container {
      background: #ffffff;
      border: 1px solid #e5e2dc;
      border-radius: 10px;
      overflow-x: auto;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
    }
    .subs-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.88rem;
      text-align: left;
    }
    .subs-table th {
      background: #fbfaf8;
      padding: 12px 16px;
      font-weight: 600;
      color: #4b5563;
      border-bottom: 1px solid #e5e2dc;
      font-size: 0.8rem;
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }
    .subs-table td {
      padding: 14px 16px;
      border-bottom: 1px solid #f0ede8;
      vertical-align: middle;
    }
    .user-cell {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .user-name {
      font-weight: 600;
      color: #111827;
    }
    .user-sub {
      font-size: 0.75rem;
      color: #6b7280;
      max-width: 220px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .plan-pill-tag {
      font-size: 0.8rem;
      font-weight: 600;
      background: #f3f4f6;
      padding: 3px 8px;
      border-radius: 4px;
      color: #1f2937;
    }
    .status-pill {
      display: inline-block;
      font-size: 0.75rem;
      font-weight: 600;
      padding: 3px 8px;
      border-radius: 12px;
    }
    .status-pill.active { background: #dcfce7; color: #15803d; }
    .status-pill.canceled { background: #fee2e2; color: #b91c1c; }
    .status-pill.expired { background: #f3f4f6; color: #6b7280; }
    .date-col {
      font-family: 'DM Mono', monospace;
      font-size: 0.82rem;
      color: #4b5563;
    }
    .actions-col {
      display: flex;
      gap: 6px;
    }
    .action-btn {
      padding: 5px 10px;
      border-radius: 5px;
      font-size: 0.78rem;
      font-weight: 500;
      cursor: pointer;
      border: 1px solid #d1d5db;
      background: #fff;
    }
    .cancel-btn:hover { background: #fee2e2; border-color: #fca5a5; color: #991b1b; }
    .change-btn:hover { background: #f3f4f6; }
    .pagination-bar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 14px 20px;
      background: #fbfaf8;
      border-top: 1px solid #e5e2dc;
      font-size: 0.82rem;
      color: #6b7280;
    }
    .page-controls button {
      padding: 5px 12px;
      margin-left: 8px;
      border: 1px solid #d1d5db;
      border-radius: 4px;
      background: #fff;
      cursor: pointer;
    }
    .page-controls button:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
    .alert {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 10px 16px;
      border-radius: 6px;
      margin-bottom: 16px;
      font-size: 0.88rem;
    }
    .alert.danger { background: #fee2e2; border: 1px solid #fca5a5; color: #991b1b; }
    .alert.success { background: #dcfce7; border: 1px solid #86efac; color: #15803d; }
    .alert button { background: none; border: none; cursor: pointer; }
    .modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.4);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 999;
      padding: 20px;
    }
    .modal-card {
      background: #ffffff;
      border-radius: 12px;
      width: 100%;
      max-width: 520px;
      padding: 30px;
      box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1);
    }
    .modal-card h2 {
      font: 700 1.25rem Georgia, serif;
      color: #233b3b;
      margin: 0 0 8px;
    }
    .modal-desc {
      color: #60736d;
      font-size: 0.86rem;
      margin: 0 0 20px;
      line-height: 1.45;
    }
    .warning-text {
      color: #b91c1c;
      font-size: 0.84rem;
      background: #fef2f2;
      padding: 10px 14px;
      border-radius: 6px;
      margin-bottom: 20px;
    }
    .form-group {
      display: flex;
      flex-direction: column;
      gap: 5px;
      margin-bottom: 14px;
    }
    .form-group label {
      font-size: 0.82rem;
      font-weight: 600;
      color: #374151;
    }
    .form-group input, .form-group select {
      padding: 8px 12px;
      border: 1px solid #d1d5db;
      border-radius: 6px;
      font-size: 0.88rem;
    }
    .modal-actions {
      display: flex;
      justify-content: flex-end;
      gap: 12px;
      margin-top: 24px;
      padding-top: 16px;
      border-top: 1px solid #f3f4f6;
    }
    .btn-secondary {
      padding: 8px 16px;
      border: 1px solid #d1d5db;
      background: #fff;
      border-radius: 6px;
      cursor: pointer;
    }
    .btn-primary {
      padding: 8px 18px;
      background: #233b3b;
      color: #fff;
      border: none;
      border-radius: 6px;
      font-weight: 600;
      cursor: pointer;
    }
    .btn-primary:hover:not(:disabled) { background: #e9784d; }
    .btn-danger {
      padding: 8px 18px;
      background: #dc2626;
      color: #fff;
      border: none;
      border-radius: 6px;
      font-weight: 600;
      cursor: pointer;
    }
    .btn-danger:hover:not(:disabled) { background: #b91c1c; }
    .empty-cell { text-align: center; padding: 36px; color: #9ca3af; }
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
