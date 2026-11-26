import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../../../core/admin/admin.service';
import { AdminPlan, CreatePlanRequest, UpdatePlanRequest } from '../../../../core/admin/admin.models';

@Component({
  selector: 'app-admin-plans',
  standalone: true,
  imports: [CommonModule, FormsModule, CurrencyPipe],
  template: `
    <div class="plans-page">
      <header class="page-header">
        <div>
          <h1>Plans &amp; Pricing Management</h1>
          <p class="subtitle">Configure subscription tiers, limits, and pricing. (Story 63)</p>
        </div>
        <button class="primary-btn" (click)="openCreateModal()">+ Create New Plan</button>
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

      <!-- Plans Table -->
      <div class="table-container">
        <table class="plans-table">
          <thead>
            <tr>
              <th>Order</th>
              <th>Code / Name</th>
              <th>Price</th>
              <th>Interval</th>
              <th>Room Limits</th>
              <th>AI Daily Quota</th>
              <th>Subscribers</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            @for (plan of adminService.plans(); track plan.id) {
              <tr [class.inactive-row]="!plan.active">
                <td class="order-col">{{ plan.sortOrder }}</td>
                <td>
                  <div class="plan-ident">
                    <span class="plan-name">{{ plan.name }}</span>
                    <span class="plan-code-badge">{{ plan.code }}</span>
                  </div>
                  @if (plan.description) {
                    <div class="plan-desc">{{ plan.description }}</div>
                  }
                </td>
                <td class="price-col">
                  <strong>{{ (plan.priceCents / 100) | currency:plan.currency:'symbol':'1.0-2' }}</strong>
                </td>
                <td>{{ plan.billingInterval }}</td>
                <td>
                  <div class="limit-badge">{{ plan.maxActiveRooms }} rooms / {{ plan.maxParticipantsPerRoom }} users</div>
                </td>
                <td>
                  <span class="tier-badge" [ngClass]="plan.aiTier.toLowerCase()">
                    {{ plan.aiDailyLimit }} req ({{ plan.aiTier }})
                  </span>
                </td>
                <td class="subs-count">
                  <span class="count-pill">{{ plan.activeSubscribersCount }}</span>
                </td>
                <td>
                  @if (plan.active) {
                    <span class="status-pill active">Active</span>
                  } @else {
                    <span class="status-pill archived">Archived</span>
                  }
                </td>
                <td class="actions-col">
                  <button class="action-btn edit-btn" (click)="openEditModal(plan)">Edit</button>
                  @if (plan.code !== 'FREE') {
                    <button class="action-btn toggle-btn" (click)="togglePlanStatus(plan)">
                      {{ plan.active ? 'Deactivate' : 'Activate' }}
                    </button>
                  } @else {
                    <span class="immutable-notice" title="FREE plan is protected and cannot be deactivated">Protected</span>
                  }
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="9" class="empty-cell">No plans found.</td>
              </tr>
            }
          </tbody>
        </table>
      </div>

      <!-- Create Plan Modal -->
      @if (showCreateModal) {
        <div class="modal-backdrop">
          <div class="modal-card">
            <h2>Create New Plan</h2>
            <form (ngSubmit)="submitCreate()">
              <div class="form-grid">
                <div class="form-group">
                  <label>Plan Code *</label>
                  <input type="text" [(ngModel)]="createForm.code" name="code" placeholder="e.g. PRO" required style="text-transform: uppercase;">
                  <small>Immutable identifier (e.g. PRO, ENTERPRISE)</small>
                </div>
                <div class="form-group">
                  <label>Plan Name *</label>
                  <input type="text" [(ngModel)]="createForm.name" name="name" placeholder="e.g. Professional" required>
                </div>
                <div class="form-group full-width">
                  <label>Description</label>
                  <textarea [(ngModel)]="createForm.description" name="description" rows="2" placeholder="Plan highlights..."></textarea>
                </div>
                <div class="form-group">
                  <label>Price (USD) *</label>
                  <input type="number" step="0.01" min="0" [(ngModel)]="createPriceDollars" name="priceDollars" required>
                </div>
                <div class="form-group">
                  <label>Billing Interval *</label>
                  <select [(ngModel)]="createForm.billingInterval" name="billingInterval">
                    <option value="MONTHLY">MONTHLY</option>
                    <option value="ANNUAL">ANNUAL</option>
                  </select>
                </div>
                <div class="form-group">
                  <label>Max Active Rooms *</label>
                  <input type="number" min="1" [(ngModel)]="createForm.maxActiveRooms" name="maxActiveRooms" required>
                </div>
                <div class="form-group">
                  <label>Max Users / Room *</label>
                  <input type="number" min="1" [(ngModel)]="createForm.maxParticipantsPerRoom" name="maxParticipantsPerRoom" required>
                </div>
                <div class="form-group">
                  <label>AI Daily Limit *</label>
                  <input type="number" min="0" [(ngModel)]="createForm.aiDailyLimit" name="aiDailyLimit" required>
                </div>
                <div class="form-group">
                  <label>AI Tier *</label>
                  <select [(ngModel)]="createForm.aiTier" name="aiTier">
                    <option value="FREE">FREE</option>
                    <option value="PREMIUM">PREMIUM</option>
                  </select>
                </div>
                <div class="form-group">
                  <label>Sort Order *</label>
                  <input type="number" [(ngModel)]="createForm.sortOrder" name="sortOrder" required>
                </div>
              </div>

              <div class="modal-actions">
                <button type="button" class="cancel-btn" (click)="showCreateModal = false">Cancel</button>
                <button type="submit" class="save-btn" [disabled]="submitting">
                  {{ submitting ? 'Saving...' : 'Create Plan' }}
                </button>
              </div>
            </form>
          </div>
        </div>
      }

      <!-- Edit Plan Modal -->
      @if (showEditModal && editingPlan) {
        <div class="modal-backdrop">
          <div class="modal-card">
            <h2>Edit Plan: {{ editingPlan.name }} ({{ editingPlan.code }})</h2>
            <form (ngSubmit)="submitEdit()">
              <div class="form-grid">
                <div class="form-group">
                  <label>Plan Code (Immutable)</label>
                  <input type="text" [value]="editingPlan.code" disabled class="disabled-input">
                </div>
                <div class="form-group">
                  <label>Plan Name *</label>
                  <input type="text" [(ngModel)]="editForm.name" name="editName" required>
                </div>
                <div class="form-group full-width">
                  <label>Description</label>
                  <textarea [(ngModel)]="editForm.description" name="editDesc" rows="2"></textarea>
                </div>
                <div class="form-group">
                  <label>Price (USD) *</label>
                  @if (editingPlan.code === 'FREE') {
                    <input type="number" value="0.00" disabled class="disabled-input" title="FREE plan price cannot exceed $0">
                    <small class="lock-notice">🔒 FREE plan price is locked at $0.00</small>
                  } @else {
                    <input type="number" step="0.01" min="0" [(ngModel)]="editPriceDollars" name="editPriceDollars" required>
                  }
                </div>
                <div class="form-group">
                  <label>Billing Interval *</label>
                  <select [(ngModel)]="editForm.billingInterval" name="editInterval">
                    <option value="MONTHLY">MONTHLY</option>
                    <option value="ANNUAL">ANNUAL</option>
                  </select>
                </div>
                <div class="form-group">
                  <label>Max Active Rooms *</label>
                  <input type="number" min="1" [(ngModel)]="editForm.maxActiveRooms" name="editRooms" required>
                </div>
                <div class="form-group">
                  <label>Max Users / Room *</label>
                  <input type="number" min="1" [(ngModel)]="editForm.maxParticipantsPerRoom" name="editUsers" required>
                </div>
                <div class="form-group">
                  <label>AI Daily Limit *</label>
                  <input type="number" min="0" [(ngModel)]="editForm.aiDailyLimit" name="editAiLimit" required>
                </div>
                <div class="form-group">
                  <label>AI Tier *</label>
                  <select [(ngModel)]="editForm.aiTier" name="editAiTier">
                    <option value="FREE">FREE</option>
                    <option value="PREMIUM">PREMIUM</option>
                  </select>
                </div>
                <div class="form-group">
                  <label>Active Status *</label>
                  @if (editingPlan.code === 'FREE') {
                    <input type="text" value="Always Active" disabled class="disabled-input">
                    <small class="lock-notice">🔒 FREE plan cannot be deactivated</small>
                  } @else {
                    <select [(ngModel)]="editForm.active" name="editActive">
                      <option [ngValue]="true">Active</option>
                      <option [ngValue]="false">Archived / Disabled</option>
                    </select>
                  }
                </div>
                <div class="form-group">
                  <label>Sort Order *</label>
                  <input type="number" [(ngModel)]="editForm.sortOrder" name="editSortOrder" required>
                </div>
              </div>

              <div class="modal-actions">
                <button type="button" class="cancel-btn" (click)="showEditModal = false">Cancel</button>
                <button type="submit" class="save-btn" [disabled]="submitting">
                  {{ submitting ? 'Updating...' : 'Save Changes' }}
                </button>
              </div>
            </form>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .plans-page {
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
    .alert {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 11px 16px;
      border-radius: 12px;
      margin-bottom: 16px;
      font-size: 0.88rem;
    }
    .alert.danger {
      background: var(--ms-error-bg);
      border: 1px solid var(--ms-error-border);
      color: var(--ms-coral, #FF9A8F);
    }
    .alert.success {
      background: var(--ms-success-bg);
      border: 1px solid var(--ms-success-border);
      color: var(--ms-mint, #34D399);
    }
    .alert button {
      background: none;
      border: none;
      color: inherit;
      cursor: pointer;
      font-size: 1rem;
    }
    .table-container {
      background: var(--ms-surface, #0D1438);
      border: 1px solid var(--ms-border, #27316E);
      border-radius: 16px;
      overflow-x: auto;
      box-shadow: var(--ms-shadow-card);
    }
    .plans-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.88rem;
      text-align: left;
    }
    .plans-table th {
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
    .plans-table td {
      padding: 14px 16px;
      border-bottom: 1px solid var(--ms-border-subtle);
      vertical-align: middle;
      color: var(--ms-text-secondary, #C5CEEE);
    }
    .inactive-row {
      opacity: 0.55;
    }
    .order-col {
      font-family: var(--ms-font-mono, monospace);
      font-weight: 600;
      color: var(--ms-text-muted, #9AA5CE);
    }
    .plan-ident {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 3px;
    }
    .plan-name {
      font-weight: 700;
      color: var(--ms-text, #F4F6FF);
    }
    .plan-code-badge {
      font-family: var(--ms-font-mono, monospace);
      font-size: 0.72rem;
      background: var(--ms-surface-raised, #141D4F);
      border: 1px solid var(--ms-border, #27316E);
      padding: 2px 7px;
      border-radius: 6px;
      color: var(--ms-primary-light, #B4BEFF);
    }
    .plan-desc {
      font-size: 0.78rem;
      color: var(--ms-text-muted, #9AA5CE);
      max-width: 280px;
    }
    .price-col {
      font-family: var(--ms-font-mono, monospace);
      font-size: 0.95rem;
      color: var(--ms-gold, #FFC83D);
    }
    .limit-badge {
      font-size: 0.8rem;
      color: var(--ms-text-secondary, #C5CEEE);
    }
    .tier-badge {
      display: inline-block;
      font-family: var(--ms-font-mono, monospace);
      font-size: 0.74rem;
      font-weight: 700;
      padding: 3px 8px;
      border-radius: 6px;
    }
    .tier-badge.free { background: var(--ms-surface-raised, #141D4F); color: var(--ms-text-muted, #9AA5CE); }
    .tier-badge.premium { background: rgba(139, 92, 246, 0.22); color: #DDD6FE; border: 1px solid rgba(139, 92, 246, 0.45); }
    .subs-count .count-pill {
      font-family: var(--ms-font-mono, monospace);
      font-weight: 700;
      padding: 2px 9px;
      border-radius: 999px;
      background: var(--ms-success-bg);
      color: var(--ms-mint, #34D399);
      border: 1px solid var(--ms-success-border);
    }
    .status-pill {
      display: inline-block;
      font-size: 0.74rem;
      font-weight: 700;
      padding: 3px 9px;
      border-radius: 999px;
    }
    .status-pill.active { background: var(--ms-success-bg); color: var(--ms-mint, #34D399); }
    .status-pill.archived { background: var(--ms-error-bg); color: var(--ms-coral, #FF9A8F); }
    .actions-col {
      display: flex;
      gap: 8px;
      align-items: center;
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
    .edit-btn:hover { border-color: var(--ms-primary, #6F82F2); color: #FFFFFF; }
    .toggle-btn:hover { background: var(--ms-error-bg); border-color: var(--ms-error-border); color: var(--ms-coral, #FF9A8F); }
    .immutable-notice {
      font-size: 0.75rem;
      color: var(--ms-text-subtle, #6F7AA8);
      font-style: italic;
    }
    .empty-cell {
      text-align: center;
      padding: 32px;
      color: var(--ms-text-muted, #9AA5CE);
    }
    /* Modals */
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
      max-width: 600px;
      max-height: 90vh;
      overflow-y: auto;
      padding: 30px;
      box-shadow: var(--ms-shadow-glass);
      color: var(--ms-text, #F4F6FF);
    }
    .modal-card h2 {
      font-family: var(--ms-font-display, 'Bricolage Grotesque', sans-serif);
      font-size: 1.3rem;
      font-weight: 700;
      color: var(--ms-text, #F4F6FF);
      margin: 0 0 20px;
    }
    .form-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
    }
    .form-group {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .form-group.full-width {
      grid-column: 1 / -1;
    }
    .form-group label {
      font-family: var(--ms-font-mono, monospace);
      font-size: 0.74rem;
      font-weight: 700;
      color: var(--ms-text-muted, #9AA5CE);
      text-transform: uppercase;
    }
    .form-group input, .form-group select, .form-group textarea {
      padding: 9px 12px;
      border: 1px solid var(--ms-border, #27316E);
      border-radius: 10px;
      background: var(--ms-surface-raised, #141D4F);
      color: var(--ms-text, #F4F6FF);
      font-size: 0.88rem;
      outline: none;
    }
    .form-group input:focus, .form-group select:focus, .form-group textarea:focus {
      border-color: var(--ms-primary, #6F82F2);
      box-shadow: 0 0 0 3px rgba(111, 130, 242, 0.25);
    }
    .disabled-input {
      opacity: 0.6;
      cursor: not-allowed;
    }
    .lock-notice {
      color: var(--ms-gold, #FFC83D);
      font-size: 0.75rem;
    }
    .modal-actions {
      display: flex;
      justify-content: flex-end;
      gap: 12px;
      margin-top: 24px;
      padding-top: 16px;
      border-top: 1px solid var(--ms-border-subtle);
    }
    .cancel-btn {
      padding: 9px 16px;
      border: 1px solid var(--ms-border, #27316E);
      background: transparent;
      color: var(--ms-text-muted, #9AA5CE);
      border-radius: 10px;
      font-weight: 600;
      cursor: pointer;
    }
    .cancel-btn:hover {
      border-color: var(--ms-primary-light, #B4BEFF);
      color: #FFFFFF;
    }
    .save-btn {
      padding: 9px 18px;
      background: var(--ms-primary, #6F82F2);
      color: #FFFFFF;
      border: none;
      border-radius: 10px;
      font-weight: 700;
      cursor: pointer;
    }
    .save-btn:hover:not(:disabled) {
      background: var(--ms-primary-hover, #8294F8);
      box-shadow: var(--ms-glow-primary);
    }
  `]
})
export class AdminPlansComponent implements OnInit {
  protected readonly adminService = inject(AdminService);

  showCreateModal = false;
  showEditModal = false;
  editingPlan: AdminPlan | null = null;
  submitting = false;
  actionError: string | null = null;
  actionSuccess: string | null = null;

  createPriceDollars = 9.0;
  createForm: CreatePlanRequest = {
    code: '',
    name: '',
    description: '',
    priceCents: 900,
    currency: 'USD',
    billingInterval: 'MONTHLY',
    maxActiveRooms: 10,
    maxParticipantsPerRoom: 15,
    aiDailyLimit: 20,
    aiTier: 'PREMIUM',
    sortOrder: 10,
  };

  editPriceDollars = 0.0;
  editForm: UpdatePlanRequest = {
    name: '',
    description: '',
    priceCents: 0,
    currency: 'USD',
    billingInterval: 'MONTHLY',
    maxActiveRooms: 1,
    maxParticipantsPerRoom: 1,
    aiDailyLimit: 0,
    aiTier: 'FREE',
    active: true,
    sortOrder: 1,
  };

  ngOnInit(): void {
    this.adminService.loadPlans().subscribe();
  }

  openCreateModal(): void {
    this.actionError = null;
    this.actionSuccess = null;
    this.createPriceDollars = 9.0;
    this.createForm = {
      code: '',
      name: '',
      description: '',
      priceCents: 900,
      currency: 'USD',
      billingInterval: 'MONTHLY',
      maxActiveRooms: 10,
      maxParticipantsPerRoom: 15,
      aiDailyLimit: 20,
      aiTier: 'PREMIUM',
      sortOrder: 10,
    };
    this.showCreateModal = true;
  }

  submitCreate(): void {
    this.submitting = true;
    this.actionError = null;
    this.createForm.priceCents = Math.round(this.createPriceDollars * 100);

    this.adminService.createPlan(this.createForm).subscribe({
      next: (res) => {
        this.submitting = false;
        this.showCreateModal = false;
        this.actionSuccess = `Successfully created plan ${res.name} (${res.code}).`;
      },
      error: (err) => {
        this.submitting = false;
        this.actionError = err?.error?.message || 'Failed to create plan.';
      }
    });
  }

  openEditModal(plan: AdminPlan): void {
    this.actionError = null;
    this.actionSuccess = null;
    this.editingPlan = plan;
    this.editPriceDollars = plan.priceCents / 100;
    this.editForm = {
      name: plan.name,
      description: plan.description,
      priceCents: plan.priceCents,
      currency: plan.currency,
      billingInterval: plan.billingInterval,
      maxActiveRooms: plan.maxActiveRooms,
      maxParticipantsPerRoom: plan.maxParticipantsPerRoom,
      aiDailyLimit: plan.aiDailyLimit,
      aiTier: plan.aiTier,
      active: plan.active,
      sortOrder: plan.sortOrder,
    };
    this.showEditModal = true;
  }

  submitEdit(): void {
    if (!this.editingPlan) return;
    this.submitting = true;
    this.actionError = null;

    if (this.editingPlan.code === 'FREE') {
      this.editForm.priceCents = 0;
      this.editForm.active = true;
    } else {
      this.editForm.priceCents = Math.round(this.editPriceDollars * 100);
    }

    this.adminService.updatePlan(this.editingPlan.id, this.editForm).subscribe({
      next: (res) => {
        this.submitting = false;
        this.showEditModal = false;
        this.actionSuccess = `Successfully updated plan ${res.name} (${res.code}).`;
      },
      error: (err) => {
        this.submitting = false;
        this.actionError = err?.error?.message || 'Failed to update plan.';
      }
    });
  }

  togglePlanStatus(plan: AdminPlan): void {
    if (plan.code === 'FREE') return;
    this.actionError = null;
    this.actionSuccess = null;

    this.adminService.togglePlanStatus(plan.id, !plan.active).subscribe({
      next: (res) => {
        this.actionSuccess = `Plan ${res.name} is now ${res.active ? 'active' : 'archived'}.`;
      },
      error: (err) => {
        this.actionError = err?.error?.message || 'Failed to toggle plan status.';
      }
    });
  }
}
