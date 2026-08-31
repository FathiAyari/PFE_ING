import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth.guard';

export const routes: Routes = [
  {
    path: 'home',
    loadComponent: () => import('./pages/home/home.component').then(m => m.HomeComponent),
    title: 'PFE — Cloud Governance'
  },
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
    path: 'request-application',
    loadComponent: () => import('./pages/request-application/request-application.component').then(m => m.RequestApplicationComponent),
    title: 'Request Application'
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell.component').then(m => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard',     loadComponent: () => import('./pages/dashboard/dashboard.component').then(m => m.DashboardComponent), title: 'Dashboard' },
      { path: 'applications',          loadComponent: () => import('./pages/applications/applications.component').then(m => m.ApplicationsComponent), title: 'Applications' },
      { path: 'applications/requests', loadComponent: () => import('./pages/application-requests/application-requests.component').then(m => m.ApplicationRequestsComponent), title: 'Application Requests' },
      { path: 'images/safe',   loadComponent: () => import('./pages/safe-images/safe-images.component').then(m => m.SafeImagesComponent), title: 'SAFE Images' },
      { path: 'images/unsafe', loadComponent: () => import('./pages/unsafe-images/unsafe-images.component').then(m => m.UnsafeImagesComponent), title: 'UNSAFE Images' },
      { path: 'pipelines',     loadComponent: () => import('./pages/pipelines/pipelines.component').then(m => m.PipelinesComponent), title: 'Pipelines' },
      { path: 'alerts',        loadComponent: () => import('./pages/alerts/alerts.component').then(m => m.AlertsComponent), title: 'Security Alerts' },
      { path: 'infra-live',    loadComponent: () => import('./pages/infra-live/infra-live.component').then(m => m.InfraLiveComponent), title: 'Live Infrastructure' },
      { path: 'admin',         loadComponent: () => import('./pages/admin/admin.component').then(m => m.AdminComponent), title: 'Admin' }
    ]
  },
  { path: '**', redirectTo: '' }
];
