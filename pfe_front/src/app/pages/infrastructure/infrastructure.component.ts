import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { SystemNode } from '../../core/models';

@Component({
  selector: 'app-infrastructure',
  standalone: true,
  imports: [CommonModule, DatePipe, DecimalPipe],
  templateUrl: './infrastructure.component.html',
  styleUrl: './infrastructure.component.css'
})
export class InfrastructureComponent implements OnInit {
  private api = inject(ApiService);
  nodes = signal<SystemNode[]>([]);
  health = signal<{ up: number; total: number; healthy: boolean } | null>(null);

  ngOnInit() { this.load(); }

  load() {
    this.api.getNodes().subscribe(n => this.nodes.set(n));
    this.api.getInfraHealth().subscribe(h => this.health.set(h));
  }

  cls(value: number): string {
    if (value >= 80) return 'danger';
    if (value >= 60) return 'warn';
    return '';
  }

  statusClass(s: string): string {
    return s.toLowerCase();
  }
}
