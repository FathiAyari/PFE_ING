import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ThemeService } from '../../core/theme.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './home.component.html'
})
export class HomeComponent {
  theme = inject(ThemeService);

  features = [
    { icon: '▤', title: 'Application Onboarding', text: 'Request cloud resources for a new app in minutes — no Azure Portal, no manual tickets.' },
    { icon: '⛭', title: 'CI/CD Monitoring', text: 'Every pipeline run, build and deployment tracked in one place with live status.' },
    { icon: '●', title: 'Image Governance', text: 'Docker images classified SAFE or UNSAFE. Only SAFE images can be deployed.' },
    { icon: '⚠', title: 'Vulnerability Alerts', text: 'CVE findings from scanners surfaced and triaged, with acknowledge workflow.' },
    { icon: '⟳', title: 'Live Azure Inventory', text: 'Real-time view of every Azure resource, with drift detection and change history.' },
    { icon: '⚙', title: 'Immutable Audit Log', text: 'Every state-changing action recorded for compliance — who did what, when.' }
  ];

  steps = [
    { n: 1, title: 'Request', text: 'A developer submits an onboarding request from the public form — no account needed.' },
    { n: 2, title: 'Review', text: 'A PFE administrator reviews the requested infrastructure and approves or rejects it.' },
    { n: 3, title: 'Provision', text: 'On approval, PFE registers the Azure resources and issues integration credentials.' },
    { n: 4, title: 'Monitor', text: 'The app connects its CI/CD to PFE and every deployment is monitored automatically.' }
  ];
}

