import type {
  DeliveryLog,
  ImportSession,
  PolicyRecord,
  StudentRecord,
} from '../mockData'

export type ApiResponse<T> = {
  success: boolean
  message: string
  data: T
}

export type LoginRequest = {
  username: string
  password: string
}

export type LoginResponse = {
  token: string
  role: string
  displayName: string
}

export type PushPreviewRequest = {
  grade: string
  major: string
  identity: string
}

export type PushPreviewResponse = {
  recipients: StudentRecord[]
  total: number
}

export type SendPushRequest = PushPreviewRequest & {
  title: string
  content: string
  channels: string[]
}

export type DashboardResponse = {
  pendingNotificationCount: number
  targetStudentCount: number
  recentDeliveryCount: number
  latestImportSuccessRate: number
}

export type ImportNotificationRow = {
  title: string
  category: string
  grade: string
  major: string
  channel: string
  publishAt: string
  status: string
}

export type KnowledgeDocumentItem = {
  id: string
  title: string
  fileName: string
  fileType: string
  sourceUrl?: string
  uploadedAt?: string
}

export type CurriculumSummary = {
  id: string
  fileName: string
  version: string
  programName: string
  requiredModules: number
  requiredCourses: number
  uploadedAt?: string
}

const TOKEN_KEY = 'rucapp-admin-token'
const API_BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? ''
type BackendRecipient = {
  id: number
  studentNo?: string
  realName: string
  grade: string
  major: string
  identity: string
}

type BackendPushPreviewResponse = {
  recipients: BackendRecipient[]
  total: number
}

type ImportNotificationsResult = {
  importSession: ImportSession
  notifications: PolicyRecord[]
}

const hasLocalStorage = () =>
  typeof window !== 'undefined' && typeof window.localStorage !== 'undefined'

export const adminApi = {
  async login(payload: LoginRequest) {
    const response = await request<LoginResponse>('/auth/login', {
      method: 'POST',
      body: payload,
      withAuth: false,
    })

    if (hasLocalStorage()) {
      window.localStorage.setItem(TOKEN_KEY, response.data.token)
    }

    return response
  },

  async getDashboard(filter: PushPreviewRequest) {
    return request<DashboardResponse>('/admin/dashboard', {
      method: 'POST',
      body: filter,
    })
  },

  async listNotifications() {
    return request<PolicyRecord[]>('/admin/notifications', {
      method: 'GET',
    })
  },

  async listDeliveryLogs() {
    return request<DeliveryLog[]>('/admin/push/logs', {
      method: 'GET',
    })
  },

  async listImportSessions() {
    return request<ImportSession[]>('/admin/import/sessions', {
      method: 'GET',
    })
  },

  async previewPush(payload: PushPreviewRequest) {
    const response = await request<BackendPushPreviewResponse>('/admin/push/preview', {
      method: 'POST',
      body: payload,
    })

    return {
      ...response,
      data: {
        recipients: response.data.recipients.map(mapRecipient),
        total: response.data.total,
      },
    }
  },

  async sendPush(payload: SendPushRequest) {
    return request<DeliveryLog>('/admin/push/send', {
      method: 'POST',
      body: payload,
    })
  },

  async importNotifications(fileName: string, rows: ImportNotificationRow[]) {
    return request<ImportNotificationsResult>(
      `/admin/import/notifications?fileName=${encodeURIComponent(fileName)}`,
      {
        method: 'POST',
        body: rows,
      },
    )
  },

  async resetMockData() {
    return Promise.resolve<ApiResponse<boolean>>({
      success: true,
      message: '当前已切换到真实后端，可重新拉取最新数据',
      data: true,
    })
  },

  async uploadKnowledgeDocument(file: File, title?: string, sourceUrl?: string) {
    return uploadFile<KnowledgeDocumentItem>('/admin/knowledge/documents', file, {
      title,
      sourceUrl,
    })
  },

  async listKnowledgeDocuments() {
    return request<KnowledgeDocumentItem[]>('/admin/knowledge/documents', {
      method: 'GET',
    })
  },

  async rebuildKnowledgeBase() {
    return request<{ chunkCount: number }>('/admin/knowledge/rebuild', {
      method: 'POST',
      body: {},
    })
  },

  async uploadCurriculum(file: File) {
    return uploadFile<CurriculumSummary>('/admin/curriculum/upload', file)
  },

  async getLatestCurriculum() {
    return request<CurriculumSummary>('/admin/curriculum/latest', {
      method: 'GET',
    })
  },
}

type RequestOptions = {
  method?: 'GET' | 'POST'
  body?: unknown
  withAuth?: boolean
}

const getToken = () =>
  hasLocalStorage() ? window.localStorage.getItem(TOKEN_KEY) ?? '' : ''

async function request<T>(path: string, options: RequestOptions): Promise<ApiResponse<T>> {
  const headers: Record<string, string> = {}
  const token = getToken()

  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  if (options.withAuth !== false && token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(buildUrl(path), {
    method: options.method ?? 'GET',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })

  const body = await parseApiResponse<T>(response)
  if (!response.ok || body.success !== true) {
    throw new Error(body.message || `请求失败：${response.status}`)
  }
  return body
}

async function uploadFile<T>(
  path: string,
  file: File,
  extraFields?: Record<string, string | undefined>,
): Promise<ApiResponse<T>> {
  const formData = new FormData()
  formData.append('file', file)
  Object.entries(extraFields ?? {}).forEach(([key, value]) => {
    if (value && value.trim()) {
      formData.append(key, value.trim())
    }
  })

  const response = await fetch(buildUrl(path), {
    method: 'POST',
    headers: getToken() ? { Authorization: `Bearer ${getToken()}` } : undefined,
    body: formData,
  })

  const body = await parseApiResponse<T>(response)
  if (!response.ok || body.success !== true) {
    throw new Error(body.message || `上传失败：${response.status}`)
  }
  return body
}

async function parseApiResponse<T>(response: Response): Promise<ApiResponse<T>> {
  const text = await response.text()

  if (!text.trim()) {
    return {
      success: response.ok,
      message: response.ok ? '操作成功' : `请求失败：${response.status}`,
      data: undefined as T,
    }
  }

  try {
    return JSON.parse(text) as ApiResponse<T>
  } catch {
    throw new Error(`接口返回了非 JSON 响应（HTTP ${response.status}）`)
  }
}

function mapRecipient(recipient: BackendRecipient): StudentRecord {
  const studentId = recipient.studentNo?.trim() || String(recipient.id)
  return {
    id: studentId,
    name: recipient.realName,
    grade: recipient.grade,
    major: recipient.major,
    identity: recipient.identity,
    email: recipient.studentNo ? `${recipient.studentNo}@ruc.edu.cn` : '',
  }
}

function buildUrl(path: string) {
  return `${API_BASE}${path}`
}
