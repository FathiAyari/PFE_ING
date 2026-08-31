import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { Application } from '../../core/models';

@Component({
  selector: 'app-application-requests',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe],
  templateUrl: './application-requests.component.html',
  styleUrl: './application-requests.component.css'
})
export class ApplicationRequestsComponent implements OnInit {
  private api = inject(ApiService);

  pending = signal<Application[]>([]);
  loading = signal(false);
  selected = signal<Application | null>(null);
  busyId = signal<number | null>(null);
  rejectReason = '';
  message = signal<{ type: 'ok' | 'err'; text: string } | null>(null);

  ngOnInit() { this.refresh(); }

  refresh() {
    this.loading.set(true);
    this.api.getPendingApplications().subscribe({
      next: d => { this.pending.set(d); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  review(app: Application) {
    this.rejectReason = '';
    this.message.set(null);
    this.selected.set(app);
  }
  close() { this.selected.set(null); }

  approve(app: Application) {
    this.busyId.set(app.id);
    this.message.set(null);
    this.api.approveApplication(app.id).subscribe({
      next: res => {
        this.busyId.set(null);
        this.selected.set(res);
        this.message.set({
          type: 'ok',
          text: `Approved "${res.name}". Registered ${res.resources.length} Azure resources · ${res.applicationCode}`
        });
        this.refresh();
      },
      error: e => {
        this.busyId.set(null);
        this.message.set({ type: 'err', text: e?.error?.error ?? 'Approval failed' });
      }
    });
  }

  reject(app: Application) {
    this.busyId.set(app.id);
    this.message.set(null);
    this.api.rejectApplication(app.id, this.rejectReason).subscribe({
      next: res => {
        this.busyId.set(null);
        this.selected.set(res);
        this.message.set({ type: 'ok', text: `Rejected "${res.name}"` });
        this.refresh();
      },
      error: e => {
        this.busyId.set(null);
        this.message.set({ type: 'err', text: e?.error?.error ?? 'Rejection failed' });
      }
    });
  }

  resourcePreview(app: Application): string[] {
    const slug = (app.name || 'app').toLowerCase().replace(/[^a-z0-9]/g, '') || 'app';
    const out = [`${slug}-rg`];
    if (app.needsContainerRegistry) out.push(`${slug}acr.azurecr.io`);
    if (app.deploymentType === 'VM') out.push(`${slug}-vm`);
    else if (app.deploymentType === 'CONTAINER_APP') out.push(`${slug}-api`);
    else if (app.deploymentType === 'AKS') out.push(`${slug}-aks`);
    if (app.database === 'POSTGRESQL') out.push(`${slug}-db`);
    if (app.needsKeyVault) out.push(`${slug}-kv`);
    return out;
  }
}

