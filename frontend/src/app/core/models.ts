export interface AuthUser {
  id: number;
  name: string;
  email: string;
  jobTitle: string | null;
  owner: boolean;
  active: boolean;
  language: string;
  roles: string[];
  permissions: string[];
}

export interface LoginRequest {
  tenant: string;
  email: string;
  password: string;
}

export interface SessionResponse {
  user: AuthUser;
}

/* ---- Usuários (M1) ---- */
export interface UserView {
  id: number;
  name: string;
  email: string;
  jobTitle: string | null;
  owner: boolean;
  active: boolean;
  roles: string[];
  language: string;
}

export interface CreateUserRequest {
  name: string;
  email: string;
  jobTitle: string | null;
  roles: string[];
  language?: string;
}

export interface UpdateUserRequest {
  name: string;
  jobTitle: string | null;
  roles: string[];
  active: boolean;
  language?: string;
}

export interface CreatedUser {
  user: UserView;
  initialPassword: string;
}

/* ---- Papéis & permissões (M1) ---- */
export interface RoleView {
  id: number;
  name: string;
  label: string;
  system: boolean;
  permissions: string[];
}

export interface PermissionGroup {
  group: string;
  permissions: string[];
}

export interface PermissionCatalog {
  groups: PermissionGroup[];
}

export interface SaveRoleRequest {
  name: string;
  label: string;
  permissions: string[];
}

/* ---- Formulários (M2 / builder v2) ---- */
export interface FieldType {
  code: string;
  label: string;
  hasOptions: boolean;
  kind: string; // input | single | multiple | layout
}

export interface FieldValidation {
  pattern?: string | null;
  patternMessage?: string | null;
  min?: number | null;
  max?: number | null;
  positive?: boolean | null;
  integer?: boolean | null;
  minLength?: number | null;
  maxLength?: number | null;
  minDate?: string | null;
  maxDate?: string | null;
  afterField?: string | null;
  beforeField?: string | null;
  accept?: string | null;       // anexo: tipos aceitos (ex.: ".pdf,.png,image/*")
  maxSizeMb?: number | null;    // anexo: tamanho máximo por arquivo em MB
}

/** Referência de um anexo guardada no valor do campo (file/multifile). */
export interface AttachmentRef {
  id: number;
  name: string;
  size: number;
  mime: string | null;
}

/** Geolocalização coletada no ato de assinar (best-effort). */
export interface SignatureGeo {
  status: 'ok' | 'denied' | 'unavailable' | 'unknown';
  lat: number | null;
  lon: number | null;
  accuracy: number | null;
}

/** Valor de um campo de assinatura (retornado pela captura; guardado no valor do campo). */
export interface SignatureValue {
  signatureId: number;
  style: 'drawn' | 'selfie';
  signedAt: string;
  signer: { id: number | null; name: string | null; email: string | null };
  ip: string | null;
  device: string | null;
  geo: SignatureGeo;
}

export interface FieldCondition {
  field: string;
  operator: string;
  value: string | null;
}

export interface FormField {
  id: string | null;
  key: string | null;
  type: string;
  label: string;
  placeholder: string | null;
  help: string | null;
  required: boolean;
  options: string[] | null;
  display?: string | null;       // single: combo|radio ; multiselect: combo|checkbox
  width?: string | null;         // full|half|third
  validation?: FieldValidation | null;
  visibleWhen?: FieldCondition | null;
  fillRole?: string | null;      // null = solicitante (no envio); papel = preenchido no fluxo
  dataModelId?: number | null;   // campo lookup: modelo de dados consultado
}

/** Seleção de um campo lookup (uma linha do modelo de dados). */
export interface LookupSelection {
  label: string;
  values: Record<string, unknown>;
}

export interface FormSummary {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  status: string;
  fields: number;
  submissions: number;
  languages: string[];
}

export interface FormDetail {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  status: string;
  fields: FormField[];
  languages: string[];
}

export interface SaveFormRequest {
  name: string;
  description: string | null;
  status: string;
  fields: FormField[];
  languages?: string[];
}

export interface SubmissionView {
  id: number;
  submittedBy: string | null;
  createdAt: string;
  data: Record<string, unknown>;
}
