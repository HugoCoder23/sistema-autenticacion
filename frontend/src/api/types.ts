export type Role = 'USER' | 'ADMIN';

export interface ApiErrorBody {
  status: number;
  code?: string;
  message: string;
  fieldErrors?: { field: string; message: string }[];
}

export interface User {
  id: number;
  name: string;
  email: string;
  roles: Role[];
  enabled: boolean;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  expiresAt: string;
  user: User;
}

export interface SessionInfo {
  id: string;
  deviceLabel?: string;
  ipAddress?: string;
  createdAt: string;
  lastUsedAt: string;
  expiresAt: string;
  current: boolean;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AuditEvent {
  id: number;
  type: string;
  outcome: 'SUCCESS' | 'FAILURE';
  userId?: number;
  actorId?: number;
  email?: string;
  ipAddress?: string;
  userAgent?: string;
  detail?: string;
  createdAt: string;
}

export interface Stats {
  totalUsers: number;
  enabledUsers: number;
  disabledUsers: number;
  admins: number;
  activeSessions: number;
  failedLogins24h: number;
}

export interface MailMessage {
  id: string;
  to: string;
  subject: string;
  body: string;
  actionUrl?: string;
  sentAt: string;
}

export interface Message {
  message: string;
}
