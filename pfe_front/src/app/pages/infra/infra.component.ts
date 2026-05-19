import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { IaCResource, IaCSummary } from '../../core/models';

interface CategoryGroup {
  key: string;
  label: string;
  items: IaCResource[];
}

@Component({
  selector: 'app-infra',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './infra.component.html'
})
export class InfraComponent implements OnInit {
  private api = inject(ApiService);

  summary = signal<IaCSummary | null>(null);
  loading = signal(false);
  error   = signal<string | null>(null);
  lastRefresh = signal<Date | null>(null);

  groups = computed<CategoryGroup[]>(() => {
    const s = this.summary();
    if (!s) return [];
    const order = ['compute', 'network', 'security', 'registry'];
    const labels: Record<string, string> = {
      compute:  'Compute',
      network:  'Networking',
      security: 'Security',
      registry: 'Container Registry'
    };
    const byKey = new Map<string, IaCResource[]>();
    for (const r of s.resources) {
      const k = r.category || 'other';
      if (!byKey.has(k)) byKey.set(k, []);
      byKey.get(k)!.push(r);
    }
    const keys = [...byKey.keys()].sort((a, b) => {
      const ia = order.indexOf(a); const ib = order.indexOf(b);
      return (ia < 0 ? 999 : ia) - (ib < 0 ? 999 : ib);
    });
    return keys.map(k => ({ key: k, label: labels[k] ?? this.cap(k), items: byKey.get(k)! }));
  });

  appliedPct = computed(() => {
    const s = this.summary();
    if (!s || s.totalResources === 0) return 0;
    return Math.round((s.appliedResources / s.totalResources) * 100);
  });

  ngOnInit(): void { this.refresh(); }

  refresh(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.getIaC().subscribe({
      next: s => { this.summary.set(s); this.loading.set(false); this.lastRefresh.set(new Date()); },
      error: e => { this.loading.set(false); this.error.set(e?.message ?? 'Failed to load IaC manifest'); }
    });
  }

  keys(o: Record<string, string>): string[] { return o ? Object.keys(o) : []; }
  cap(s: string): string { return s ? s.charAt(0).toUpperCase() + s.slice(1) : s; }
}
