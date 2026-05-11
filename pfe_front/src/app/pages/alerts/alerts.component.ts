import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { SecurityAlert } from '../../core/models';

@Component({
  selector: 'app-alerts',
  standalone: true,
  imports: [CommonModule, DatePipe],
  templateUrl: './alerts.component.html',
  styleUrl: './alerts.component.css'
})
export class AlertsComponent implements OnInit {
  private api = inject(ApiService);
  alerts = signal<SecurityAlert[]>([]);
  filter = signal<'ALL' | 'OPEN'>('OPEN');

  visible = computed(() => this.filter() === 'OPEN'
    ? this.alerts().filter(a => !a.acknowledged)
    : this.alerts());

  ngOnInit() { this.load(); }
  load() { this.api.getAlerts().subscribe(d => this.alerts.set(d)); }

  acknowledge(a: SecurityAlert) {
    this.api.acknowledgeAlert(a.id).subscribe(updated => {
      this.alerts.update(list => list.map(x => x.id === updated.id ? updated : x));
    });
  }
}
