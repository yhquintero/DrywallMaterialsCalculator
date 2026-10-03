/** Tipos compartidos de la Consola de Licencias (espejo del DTO del backend). */

export type AppId = 'drywall_calculator' | 'keygen_pro';

export interface AppMeta {
  id: AppId;
  name: string;
  package: string;
  description: string;
  licenseCount?: number;
  hasActiveKey?: boolean;
}

export interface AuthUser {
  id: number;
  username: string;
  email: string | null;
  fullName: string;
  roleId: number;
  roleCode: string;
  roleName: string;
  roleLevel: number;
  isActive: boolean;
  mustChangePassword: boolean;
  lockedUntil: number | null;
  lastLoginAt: number | null;
  createdAt: number;
  permissions: string[];
}

export interface LoginResponse {
  user: AuthUser;
  accessToken: string;
  expiresInMinutes: number;
  https: boolean;
}

export interface LicenseJson {
  user: string;
  deviceId: string;
  creationDate: number;
  expiryDate: number;
  signature: string;
  type: string;
  issuerKey: string;
}

export type LicenseStatus =
  | 'draft'
  | 'issued'
  | 'paid'
  | 'expired'
  | 'revoked'
  | 'blocked';

export interface License {
  id: number;
  licenseKey: string;
  appId: AppId;
  appName: string;
  userName: string;
  deviceId: string;
  deviceLabel: string | null;
  planCode: string;
  planLabel: string;
  price: number;
  currency: string;
  priceText: string;
  creationDate: number;
  expiryDate: number;
  signature: string;
  licenseJson: LicenseJson | null;
  status: LicenseStatus;
  isPaid: boolean;
  paidAt: number | null;
  paymentMethod: string | null;
  email: string | null;
  phone: string | null;
  notes: string;
  source: string;
  issuedBy: number | null;
  revokedAt: number | null;
  revokeReason: string | null;
  renewedFrom: number | null;
  lastSeenAt: number | null;
  createdAt: number;
  updatedAt: number;
  daysRemaining: number;
}

export interface Plan {
  id: number;
  appId: AppId;
  code: string;
  label: string;
  typeName: string;
  days: number;
  price: number;
  currency: string;
  isActive: boolean;
  sortOrder: number;
}

export interface SigningKey {
  id: number;
  appId: AppId;
  appName: string;
  kid: string;
  algorithm: string;
  modulusBits: number;
  publicKeyB64: string;
  publicKeyPem: string;
  publicKeySha256: string;
  fingerprint: string;
  isActive: boolean;
  createdAt: number;
  rotatedAt: number | null;
  notes: string;
}

export interface Role {
  id: number;
  code: string;
  name: string;
  description: string;
  level: number;
  isSystem: boolean;
  isActive: boolean;
  permissions: string[];
  isWildcard: boolean;
  userCount?: number;
}

export interface PermissionDef {
  id: string;
  group: string;
  label: string;
  description: string;
}

export interface PermissionGroup {
  id: string;
  label: string;
}

export interface UserRecord {
  id: number;
  username: string;
  email: string | null;
  fullName: string;
  roleId: number;
  roleCode: string;
  roleName: string;
  isActive: boolean;
  mustChangePassword: boolean;
  failedAttempts: number;
  lockedUntil: number | null;
  lastLoginAt: number | null;
  lastLoginIp: string | null;
  createdAt: number;
}

export interface AuditEntry {
  id: number;
  actor_id: number | null;
  actor_name: string;
  action: string;
  entity: string;
  entity_id: string;
  app_id: string | null;
  severity: 'debug' | 'info' | 'warn' | 'critical';
  ip_address: string;
  user_agent: string;
  detail: string;
  created_at: number;
}

export interface Rate {
  code: string;
  rate: number;
  symbol: string;
  source: string;
  updatedAt: number;
  updatedBy: number | null;
}

export interface DeviceRecord {
  id: number;
  appId: AppId;
  appName: string;
  deviceId: string;
  deviceLabel: string;
  ownerName: string;
  firstSeenAt: number;
  lastSeenAt: number;
  isBlocked: boolean;
  licenseCount: number;
}

export interface LicenseStats {
  total: number;
  active: number;
  expired: number;
  revoked: number;
  pending: number;
  expiring7: number;
  expiring30: number;
  revenue: number;
  devices: number;
  blockedDevices: number;
  byApp: { appId: AppId; appName: string; total: number; paid: number; revenue: number; active: number }[];
  byPlan: { plan_label: string; total: number; revenue: number }[];
  monthly: { month: string; total: number; revenue: number }[];
  generatedAt: number;
}
