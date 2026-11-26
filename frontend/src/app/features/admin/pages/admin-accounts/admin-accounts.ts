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
    .setup-banner {
      display: flex;
      gap: 16px;
      padding: 16px 20px;
      background: var(--ms-info-bg);
      border: 1px solid var(--ms-info-border);
      border-radius: 14px;
      margin-bottom: 24px;
      align-items: flex-start;
    }
    .banner-icon {
      font-size: 1.5rem;
    }
    .banner-content h3 {
      font-family: var(--ms-font-display, 'Bricolage Grotesque', sans-serif);
      font-size: 1rem;
      font-weight: 700;
      color: var(--ms-info, #38BDF8);
      margin: 0 0 6px;
    }
    .banner-content p {
      font-size: 0.85rem;
      color: var(--ms-text-secondary, #C5CEEE);
      margin: 0 0 6px;
      line-height: 1.45;
    }
    .banner-content code {
      background: var(--ms-surface-raised, #141D4F);
      border: 1px solid var(--ms-border, #27316E);
      color: var(--ms-primary-light, #B4BEFF);
      padding: 2px 6px;
      border-radius: 6px;
      font-family: var(--ms-font-mono, monospace);
      font-size: 0.82em;
    }
    .split-layout {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 24px;
    }
    .card {
      background: var(--ms-surface, #0D1438);
      border: 1px solid var(--ms-border, #27316E);
      border-radius: 16px;
      padding: 28px;
      box-shadow: var(--ms-shadow-card);
    }
    .card h2 {
      font-family: var(--ms-font-display, 'Bricolage Grotesque', sans-serif);
      font-size: 1.18rem;
      font-weight: 700;
      color: var(--ms-text, #F4F6FF);
      margin: 0 0 6px;
    }
    .card-desc {
      color: var(--ms-text-muted, #9AA5CE);
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
      gap: 6px;
      margin-bottom: 16px;
    }
    .form-group label {
      font-family: var(--ms-font-mono, monospace);
      font-size: 0.74rem;
      font-weight: 700;
      color: var(--ms-text-muted, #9AA5CE);
      text-transform: uppercase;
    }
    .form-group input {
      padding: 9px 12px;
      border: 1px solid var(--ms-border, #27316E);
      border-radius: 10px;
      background: var(--ms-surface-raised, #141D4F);
      color: var(--ms-text, #F4F6FF);
      font-size: 0.88rem;
      outline: none;
    }
    .form-group input:focus {
      border-color: var(--ms-primary, #6F82F2);
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
      color: var(--ms-text-subtle, #6F7AA8);
      font-size: 0.75rem;
    }
    .submit-btn {
      width: 100%;
      padding: 11px;
      background: var(--ms-primary, #6F82F2);
      color: #FFFFFF;
      border: none;
      border-radius: 12px;
      font-size: 0.92rem;
      font-weight: 700;
      cursor: pointer;
      margin-top: 8px;
      transition: all 0.15s ease;
    }
    .submit-btn:hover:not(:disabled) {
      background: var(--ms-primary-hover, #8294F8);
      box-shadow: var(--ms-glow-primary);
    }
    .submit-btn:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
    .roster-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
    }
    .refresh-roster-btn {
      background: var(--ms-surface-raised, #141D4F);
      border: 1px solid var(--ms-border, #27316E);
      color: var(--ms-text-secondary, #C5CEEE);
      padding: 5px 10px;
      border-radius: 8px;
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
      background: var(--ms-surface-raised, #141D4F);
      border: 1px solid var(--ms-border, #27316E);
      border-radius: 12px;
    }
    .admin-avatar {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background: var(--ms-primary, #6F82F2);
      color: #FFFFFF;
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
      font-weight: 700;
      color: var(--ms-text, #F4F6FF);
      font-size: 0.88rem;
    }
    .admin-email {
      font-size: 0.78rem;
      color: var(--ms-text-muted, #9AA5CE);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .role-tag {
      font-family: var(--ms-font-mono, monospace);
      font-size: 0.7rem;
      font-weight: 700;
      padding: 3px 9px;
      border-radius: 999px;
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }
    .role-tag.admin {
      background: rgba(139, 92, 246, 0.2);
      color: #DDD6FE;
      border: 1px solid rgba(139, 92, 246, 0.45);
    }
    .role-tag.super-admin {
      background: rgba(255, 200, 61, 0.16);
      color: var(--ms-gold, #FFC83D);
      border: 1px solid rgba(255, 200, 61, 0.45);
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
    .empty-roster {
      text-align: center;
      padding: 30px;
      color: var(--ms-text-muted, #9AA5CE);
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
