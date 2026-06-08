import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { InfraService } from '../../core/infra.service';
import { AzureResource, SyncRun } from '../../core/models';
import { RealtimeService } from '../../core/realtime.service';

@Component({
  selector: 'app-infra-live',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './infra-live.component.html',
  styleUrls: ['./infra-live.component.css']
})
export class InfraLiveComponent implements OnInit, OnDestroy {
  private infraSvc = inject(InfraService);
  private realtime = inject(RealtimeService);

  readonly resources = signal<AzureResource[]>([]);
  readonly runs = signal<SyncRun[]>([]);
  readonly loading = signal<boolean>(false);
  readonly connected = signal<boolean>(false);
  readonly loadError = signal<string | null>(null);
  readonly flashIds = signal<Record<string, string>>({}); // azureId -> css class

  // Filters
  filterType = '';
  filterRg = '';
  filterQ = '';
  showDeleted = false;

  private subs: Subscription[] = [];

  ngOnInit(): void {
    this.refresh();
    this.subs.push(
      this.realtime.resourceChanges$().subscribe(msg => {
        this.connected.set(true);
        this.applyChange(msg.changeType, msg.resource);
      })
    );
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
  }

  refresh(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.infraSvc.list({
      type: this.filterType || undefined,
      rg: this.filterRg || undefined,
      q: this.filterQ || undefined,
      size: 200
    }).subscribe({
      next: page => { this.resources.set(page.content); this.loading.set(false); },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.loadError.set(this.toLoadError(err));
      }
    });
    this.infraSvc.syncRuns().subscribe({
      next: r => this.runs.set(r),
      error: () => {
        // Keep the page usable even if only this section fails.
      }
    });
  }

  triggerSweep(): void {
    // Public manual refresh button (kept for future HMAC-protected manual trigger).
    this.refresh();
  }

  shortType(t?: string): string {
    if (!t) return '';
    const i = t.indexOf('/');
    return i > 0 ? t.substring(i + 1) : t;
  }

  badgeClass(r: AzureResource): string {
    if (r.deletedAt) return 'badge-deleted';
    if (r.type?.toLowerCase().includes('virtualmachines')) {
      const ps = (r.powerState || '').toLowerCase();
      if (ps.includes('running'))      return 'badge-running';
      if (ps.includes('stopped') || ps.includes('deallocated')) return 'badge-stopped';
      if (ps.includes('starting') || ps.includes('restarting')) return 'badge-pending';
      return 'badge-unknown';
    }
    const ps = (r.provisioningState || '').toLowerCase();
    if (ps === 'succeeded') return 'badge-running';
    if (ps === 'failed')    return 'badge-stopped';
    return 'badge-unknown';
  }

  badgeLabel(r: AzureResource): string {
    if (r.deletedAt) return 'Deleted';
    if (r.type?.toLowerCase().includes('virtualmachines')) {
      return (r.powerState || 'Unknown').replace('PowerState/', '');
    }
    return r.provisioningState || '—';
  }

  filtered(): AzureResource[] {
    return this.resources().filter(r => this.showDeleted || !r.deletedAt);
  }

  flashFor(azureId: string): string {
    return this.flashIds()[azureId] || '';
  }

  private applyChange(change: string, r: AzureResource): void {
    const list = [...this.resources()];
    const idx = list.findIndex(x => x.azureId === r.azureId);
    if (change === 'DELETE') {
      if (idx >= 0) list[idx] = r; else list.unshift(r);
      this.flash(r.azureId, 'flash-delete');
    } else if (idx >= 0) {
      list[idx] = r;
      this.flash(r.azureId, 'flash-update');
    } else {
      list.unshift(r);
      this.flash(r.azureId, 'flash-create');
    }
    this.resources.set(list);
  }

  private flash(azureId: string, cls: string): void {
    this.flashIds.set({ ...this.flashIds(), [azureId]: cls });
    setTimeout(() => {
      const next = { ...this.flashIds() };
      delete next[azureId];
      this.flashIds.set(next);
    }, 2000);
  }

  private toLoadError(err: HttpErrorResponse): string {
    if (err.status === 0) {
      return 'Cannot reach backend API on port 8080. Start the backend and retry.';
    }
    if (err.status === 401 || err.status === 403) {
      return 'Session expired or unauthorized. Please login again.';
    }
    return `Failed to load infrastructure resources (HTTP ${err.status || 'unknown'}).`;
  }
}
