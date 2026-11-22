import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../../../core/admin/admin.service';
import { CreateAdminRequest } from '../../../../core/admin/admin.models';

@Component({
  selector: 'app-admin-accounts',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="accounts-page">
      <header class="page-header">
        <div>
          <h1>Administrator Accounts &amp; Provisioning</h1>
          <p class="subtitle">Super-admin account provisioning and Keycloak realm role management. (Story 65)</p>
        </div>
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

      <!-- Setup Notice Banner if Keycloak Admin API is unconfigured -->
      @if (adminService.adminsList() && !adminService.adminsList()?.configured) {
        <div class="setup-banner">
          <div class="banner-icon">ℹ️</div>
          <div class="banner-content">
            <h3>Keycloak Admin API Client Not Configured</h3>
            <p>
              Automated provisioning through the API requires <code>KEYCLOAK_ADMIN_CLIENT_ID</code> and
              <code>KEYCLOAK_ADMIN_CLIENT_SECRET</code> to be set in <code>subscription-service</code>.
            </p>
            <p>
              Please follow the quick setup steps in <code>docs/keycloak-admin-roles-setup.md</code> to enable this feature,
              or create roles directly in the Keycloak admin console at <code>http://localhost:8180</code>.
            </p>
          </div>
        </div>
      }

      <div class="split-layout">
        <!-- Create Admin Account Card -->
        <div class="card create-card">
          <h2>Create New Administrator</h2>
          <p class="card-desc">
            Creates a new account in the MindSpark Keycloak realm, assigns the <code>admin</code> realm role,
            and marks the temporary password for mandatory reset upon first sign-in.
          </p>

          <form (ngSubmit)="submitCreate()">
            <div class="form-row">
              <div class="form-group">
                <label>First Name *</label>
                <input type="text" [(ngModel)]="createForm.firstName" name="firstName" placeholder="Jane" required>
              </div>
              <div class="form-group">
                <label>Last Name *</label>
                <input type="text" [(ngModel)]="createForm.lastName" name="lastName" placeholder="Doe" required>
              </div>
            </div>

            <div class="form-group">
              <label>Work Email *</label>
              <input type="email" [(ngModel)]="createForm.email" name="email" placeholder="jane.doe@example.com" required>
              <small class="help-text">Used as both username and login email in Keycloak.</small>
            </div>

            <div class="form-group">
              <label>Temporary Password * (min 8 chars)</label>
              <div class="password-wrapper">
                <input [type]="showPassword ? 'text' : 'password'" [(ngModel)]="createForm.temporaryPassword" name="tempPassword" minlength="8" maxlength="128" placeholder="••••••••" required>
                <button type="button" class="eye-btn" (click)="showPassword = !showPassword">
                  {{ showPassword ? '🙈' : '👁️' }}
                </button>
              </div>
              <small class="help-text">The new admin will be forced to change this on first login.</small>
            </div>

            <button type="submit" class="submit-btn" [disabled]="submitting || (createForm.temporaryPassword.length < 8)">
              {{ submitting ? 'Provisioning...' : 'Provision Administrator Account' }}
            </button>
          </form>
        </div>

        <!-- Current Admins Roster -->
        <div class="card roster-card">
          <div class="roster-header">
            <h2>Current Realm Administrators</h2>
            <button class="refresh-roster-btn" (click)="loadAdmins()">🔄</button>
          </div>

          <div class="admin-list">
            @for (adm of adminService.adminsList()?.admins ?? []; track adm.userId) {
              <div class="admin-item">
                <div class="admin-avatar">
                  {{ (adm.firstName ? adm.firstName.charAt(0) : adm.username.charAt(0)) | uppercase }}
                </div>
                <div class="admin-info">
                  <div class="admin-name">
                    {{ adm.firstName || '' }} {{ adm.lastName || '' }}
                    @if (!adm.firstName && !adm.lastName) {
                      {{ adm.username }}
                    }
                  </div>
                  <div class="admin-email">{{ adm.email || adm.username }}</div>
                </div>
                <div class="admin-badge-col">
                  <span class="role-tag" [ngClass]="adm.role">{{ adm.role }}</span>
                </div>
              </div>
            } @empty {
              <div class="empty-roster">
                @if (adminService.adminsLoading()) {
                  <p>Loading administrators from Keycloak...</p>
                } @else {
                  <p>No administrators discovered. Check Keycloak realm configuration.</p>
                }
              </div>
            }
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .accounts-page {
      max-width: 1200px;
      margin: 0 auto;
    }
    .page-header {
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
    .setup-banner {
      display: flex;
      gap: 16px;
      padding: 16px 20px;
      background: #eff6ff;
      border: 1px solid #bfdbfe;
      border-radius: 8px;
      margin-bottom: 24px;
      align-items: flex-start;
    }
    .banner-icon {
      font-size: 1.5rem;
    }
    .banner-content h3 {
      font: 700 0.98rem Georgia, serif;
      color: #1e40af;
      margin: 0 0 6px;
    }
    .banner-content p {
      font-size: 0.85rem;
      color: #1e3a8a;
      margin: 0 0 6px;
      line-height: 1.45;
    }
    .banner-content code {
      background: #dbeafe;
      padding: 2px 6px;
      border-radius: 4px;
      font-size: 0.82em;
    }
    .split-layout {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 24px;
    }
    .card {
      background: #ffffff;
      border: 1px solid #e5e2dc;
      border-radius: 10px;
      padding: 28px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
    }
    .card h2 {
      font: 700 1.15rem Georgia, serif;
      color: #233b3b;
      margin: 0 0 6px;
    }
    .card-desc {
      color: #6b7280;
      font-size: 0.85rem;
      margin: 0 0 20px;
      line-height: 1.45;
    }
    .form-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 14px;
    }
    .form-group {
      display: flex;
      flex-direction: column;
      gap: 5px;
      margin-bottom: 16px;
    }
    .form-group label {
      font-size: 0.82rem;
      font-weight: 600;
      color: #374151;
    }
    .form-group input {
      padding: 9px 12px;
      border: 1px solid #d1d5db;
      border-radius: 6px;
      font-size: 0.88rem;
    }
    .password-wrapper {
      position: relative;
      display: flex;
      align-items: center;
    }
    .password-wrapper input {
      width: 100%;
      padding-right: 40px;
    }
    .eye-btn {
      position: absolute;
      right: 8px;
      background: none;
      border: none;
      cursor: pointer;
      font-size: 1rem;
    }
    .help-text {
      color: #9ca3af;
      font-size: 0.75rem;
    }
    .submit-btn {
      width: 100%;
      padding: 11px;
      background: #233b3b;
      color: #fffaf1;
      border: none;
      border-radius: 6px;
      font: 600 0.92rem inherit;
      cursor: pointer;
      margin-top: 8px;
      transition: background 0.15s ease;
    }
    .submit-btn:hover:not(:disabled) {
      background: #e9784d;
    }
    .submit-btn:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
    .roster-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
    }
    .refresh-roster-btn {
      background: none;
      border: 1px solid #e5e2dc;
      padding: 4px 8px;
      border-radius: 4px;
      cursor: pointer;
    }
    .admin-list {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    .admin-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 14px;
      background: #faf8f5;
      border: 1px solid #f0ede8;
      border-radius: 8px;
    }
    .admin-avatar {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background: #233b3b;
      color: #fff;
      display: grid;
      place-items: center;
      font-weight: 700;
      font-size: 0.88rem;
    }
    .admin-info {
      flex: 1;
      overflow: hidden;
    }
    .admin-name {
      font-weight: 600;
      color: #111827;
      font-size: 0.88rem;
    }
    .admin-email {
      font-size: 0.78rem;
      color: #6b7280;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .role-tag {
      font-size: 0.72rem;
      font-weight: 600;
      padding: 3px 8px;
      border-radius: 12px;
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }
    .role-tag.admin {
      background: #f3e8ff;
      color: #7e22ce;
      border: 1px solid #e9d5ff;
    }
    .role-tag.super-admin {
      background: #fef3c7;
      color: #b45309;
      border: 1px solid #fde68a;
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
    .empty-roster {
      text-align: center;
      padding: 30px;
      color: #9ca3af;
      font-size: 0.85rem;
    }
    @media (max-width: 860px) {
      .split-layout {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class AdminAccountsComponent implements OnInit {
  protected readonly adminService = inject(AdminService);

  showPassword = false;
  submitting = false;
  actionError: string | null = null;
  actionSuccess: string | null = null;

  createForm: CreateAdminRequest = {
    email: '',
    firstName: '',
    lastName: '',
    temporaryPassword: '',
  };

  ngOnInit(): void {
    this.loadAdmins();
  }

  loadAdmins(): void {
    this.adminService.loadAdmins().subscribe();
  }

  submitCreate(): void {
    if (this.submitting) return;
    this.submitting = true;
    this.actionError = null;
    this.actionSuccess = null;

    this.adminService.createAdmin(this.createForm).subscribe({
      next: (user) => {
        this.submitting = false;
        this.actionSuccess = `Successfully created administrator account for ${user.firstName} ${user.lastName} (${user.email}) with temporary credentials.`;
        this.createForm = {
          email: '',
          firstName: '',
          lastName: '',
          temporaryPassword: '',
        };
        this.loadAdmins();
      },
      error: (err) => {
        this.submitting = false;
        this.actionError = err?.error?.message || 'Failed to create administrator account.';
      }
    });
  }
}
