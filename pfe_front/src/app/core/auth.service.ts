import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

export interface UserInfo {
  id: number;
  username: string;
  email: string;
  role: string;
}

export interface AuthResponse {
  token: string;
  expiresAt: number;
  user: UserInfo;
}

const TOKEN_KEY = 'pfe-token';
const USER_KEY  = 'pfe-user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private base = environment.apiBaseUrl + '/auth';

  readonly token = signal<string | null>(this.read(TOKEN_KEY));
  readonly user  = signal<UserInfo | null>(this.readUser());
  readonly isAuthenticated = computed(() => !!this.token());

  login(username: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/login`, { username, password })
      .pipe(tap(res => this.persist(res)));
  }

  register(username: string, password: string, email: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/register`, { username, password, email })
      .pipe(tap(res => this.persist(res)));
  }

  me(): Observable<UserInfo> {
    return this.http.get<UserInfo>(`${this.base}/me`)
      .pipe(tap(u => { this.user.set(u); this.write(USER_KEY, JSON.stringify(u)); }));
  }

  logout() {
    this.token.set(null);
    this.user.set(null);
    try {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
    } catch {}
    this.router.navigate(['/login']);
  }

  private persist(res: AuthResponse) {
    this.token.set(res.token);
    this.user.set(res.user);
    this.write(TOKEN_KEY, res.token);
    this.write(USER_KEY, JSON.stringify(res.user));
  }

  private read(key: string): string | null {
    try { return localStorage.getItem(key); } catch { return null; }
  }
  private write(key: string, value: string) {
    try { localStorage.setItem(key, value); } catch {}
  }
  private readUser(): UserInfo | null {
    const raw = this.read(USER_KEY);
    if (!raw) return null;
    try { return JSON.parse(raw) as UserInfo; } catch { return null; }
  }
}
