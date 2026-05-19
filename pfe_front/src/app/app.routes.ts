import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/login/login.component').then(m => m.LoginComponent),
    title: 'Sign in'
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/register/register.component').then(m => m.RegisterComponent),
    title: 'Create account'
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell.component').then(m => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard',     loadComponent: () => import('./pages/dashboard/dashboard.component').then(m => m.DashboardComponent), title: 'Dashboard' },
      { path: 'images/safe',   loadComponent: () => import('./pages/safe-images/safe-images.component').then(m => m.SafeImagesComponent), title: 'SAFE Images' },
      { path: 'images/unsafe', loadComponent: () => import('./pages/unsafe-images/unsafe-images.component').then(m => m.UnsafeImagesComponent), title: 'UNSAFE Images' },
      { path: 'pipelines',     loadComponent: () => import('./pages/pipelines/pipelines.component').then(m => m.PipelinesComponent), title: 'Pipelines' },
      { path: 'alerts',        loadComponent: () => import('./pages/alerts/alerts.component').then(m => m.AlertsComponent), title: 'Security Alerts' },
      { path: 'infrastructure',loadComponent: () => import('./pages/infrastructure/infrastructure.component').then(m => m.InfrastructureComponent), title: 'Infrastructure' },
      { path: 'infra',         loadComponent: () => import('./pages/infra/infra.component').then(m => m.InfraComponent), title: 'Infra (IaC)' },
      { path: 'admin',         loadComponent: () => import('./pages/admin/admin.component').then(m => m.AdminComponent), title: 'Admin' }
    ]
  },
  { path: '**', redirectTo: '' }
];
