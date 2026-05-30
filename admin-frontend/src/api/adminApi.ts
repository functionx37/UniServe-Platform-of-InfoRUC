const API_BASE = ''

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  withAuth?: boolean
}

async function request<T>(url: string, options: RequestOptions = {}): Promise<{ success: boolean; message: string; data: T }> {
  const { method = 'GET', body, withAuth = true } = options
  
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  }

  if (withAuth) {
    const token = sessionStorage.getItem('token')
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }
  }

  const response = await fetch(url.startsWith('http') ? url : `${API_BASE}${url}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  })

  if (response.status === 401) {
    sessionStorage.removeItem('token')
    window.location.reload()
  }

  const result = await response.json()
  if (!response.ok) {
    throw new Error(result.message || '请求失败')
  }

  return result
}

async function uploadFile<T>(url: string, file: File): Promise<{ success: boolean; message: string; data: T }> {
  const formData = new FormData()
  formData.append('file', file)

  const token = sessionStorage.getItem('token')
  const headers: Record<string, string> = {}
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const response = await fetch(`${API_BASE}${url}`, {
    method: 'POST',
    headers,
    body: formData,
  })

  const result = await response.json()
  if (!response.ok || result.success === false) {
    throw new Error(result.message || '上传失败')
  }

  return result
}

export interface KnowledgeDocumentItem {
  id: string
  title: string
  fileName: string
  fileType: string
  filePath: string
  sourceUrl: string
  active: boolean
  uploadedBy: number
  uploadedAt: string
}

export interface CurriculumSummary {
  id: number
  programName: string
  fileName: string
  fileType: string
  filePath: string
  version: string
  active: boolean
  requiredCourses: number
  requiredModules: number
  uploadedBy: number
  uploadedAt: string
}

export interface ImportSessionVO {
  id: string
  fileName: string
  totalRows: number
  successRows: number
  failedRows: number
  importedAt: string
}

export interface ImportUsersResult {
  importSession: ImportSessionVO
  errors: string[]
  message: string
}

export interface ImportNotificationsResult {
  session: ImportSessionVO
  message: string
}

export const adminApi = {
  async login(payload: any) {
    const res = await request<any>('/auth/login', {
      method: 'POST',
      body: payload,
      withAuth: false,
    })
    if (res.data.token) {
      sessionStorage.setItem('token', res.data.token)
    }
    return res
  },

  async getDashboard(query: any) {
    return request<any>('/admin/dashboard', {
      method: 'POST',
      body: query,
    })
  },

  async listNotifications() {
    return request<any[]>('/admin/notifications')
  },

  async deleteNotification(id: string) {
    return request<boolean>(`/admin/notifications/${id}`, {
      method: 'DELETE',
    })
  },

  async importNotifications(fileName: string, rows: any[]) {
    return request<ImportNotificationsResult>(`/admin/import/notifications?fileName=${encodeURIComponent(fileName)}`, {
      method: 'POST',
      body: rows,
    })
  },

  async updateNotificationStatus(id: string, status: string) {
    return request<boolean>(`/admin/notifications/${id}/status`, {
      method: 'PUT',
      body: { status },
    })
  },

  async previewPush(query: any) {
    return request<any>('/admin/push/preview', {
      method: 'POST',
      body: query,
    })
  },

  async sendPush(payload: any) {
    return request<any>('/admin/push/send', {
      method: 'POST',
      body: payload,
    })
  },

  async listDeliveryLogs() {
    return request<any[]>('/admin/push/logs')
  },

  async deleteDeliveryLog(id: string) {
    return request<boolean>(`/admin/push/logs/${id}`, {
      method: 'DELETE',
    })
  },

  async listImportSessions() {
    return request<any[]>('/admin/import/sessions')
  },

  async listKnowledgeDocuments() {
    return request<KnowledgeDocumentItem[]>('/admin/knowledge/documents', {
      method: 'GET',
    })
  },

  async uploadKnowledgeDocument(file: File) {
    return uploadFile<KnowledgeDocumentItem>('/admin/knowledge/documents', file)
  },

  async updateKnowledgeDocument(id: string, payload: { title?: string; sourceUrl?: string; active?: boolean }) {
    return request<boolean>(`/admin/knowledge/documents/${id}`, {
      method: 'PUT',
      body: payload,
    })
  },

  async deleteKnowledgeDocument(id: string) {
    return request<boolean>(`/admin/knowledge/documents/${id}`, {
      method: 'DELETE',
    })
  },

  async rebuildKnowledgeBase() {
    return request<{ chunkCount: number }>('/admin/knowledge/rebuild', {
      method: 'POST',
      body: {},
    })
  },

  async bootstrapKnowledgeBase(dir?: string) {
    return request<boolean>(`/admin/knowledge/bootstrap${dir ? `?dir=${encodeURIComponent(dir)}` : ''}`, {
      method: 'POST',
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

  async deleteCurriculum(id: string) {
    return request<boolean>(`/admin/curriculum/${id}`, {
      method: 'DELETE',
    })
  },

  async listApplications(status: string = '全部') {
    return request<any[]>(`/admin/applications?status=${encodeURIComponent(status)}`, {
      method: 'GET',
    })
  },

  async deleteApplication(id: number) {
    return request<boolean>(`/admin/applications/${id}`, {
      method: 'DELETE',
    })
  },

  async getApplicationDetail(id: number) {
    return request<any>(`/admin/applications/${id}`, {
      method: 'GET',
    })
  },

  async auditApplication(id: number, action: 'pass' | 'reject' | 'withdraw', opinion: string = '') {
    return request<boolean>(`/admin/applications/${id}/audit`, {
      method: 'POST',
      body: { action, opinion },
    })
  },

  async listUsers(query: { roleId?: number; grade?: string; major?: string; keyword?: string }) {
    const params = new URLSearchParams()
    if (query.roleId) params.append('roleId', String(query.roleId))
    if (query.grade) params.append('grade', query.grade)
    if (query.major) params.append('major', query.major)
    if (query.keyword) params.append('keyword', query.keyword)
    return request<any[]>(`/admin/users?${params.toString()}`, {
      method: 'GET',
    })
  },

  async createUser(payload: any) {
    return request<any>('/admin/users', {
      method: 'POST',
      body: payload,
    })
  },

  async importUsers(rows: any[]) {
    return request<ImportUsersResult>('/admin/users/import', {
      method: 'POST',
      body: rows,
    })
  },

  async updateUser(id: number, payload: any) {
    return request<any>(`/admin/users/${id}`, {
      method: 'PUT',
      body: payload,
    })
  },

  async deleteUser(id: number) {
    return request<boolean>(`/admin/users/${id}`, {
      method: 'DELETE',
    })
  },

  async listAuditLogs(action?: string, limit: number = 20) {
    const params = new URLSearchParams()
    if (action) params.append('action', action)
    params.append('limit', String(limit))
    return request<any[]>(`/admin/audit/logs?${params.toString()}`)
  },

  async downloadFile(url: string, fileName: string) {
    const token = sessionStorage.getItem('token')
    const headers: Record<string, string> = {}
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }

    const response = await fetch(url.startsWith('http') ? url : `${API_BASE}${url}`, {
      headers
    })

    if (!response.ok) {
      throw new Error('下载失败')
    }

    const blob = await response.blob()
    const objectUrl = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = objectUrl
    a.download = fileName
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(objectUrl)
  },

  getTemplateDownloadUrl(type: 'notifications' | 'users' | 'courses') {
    return `${API_BASE}/files/templates/admin/${type}`
  }
}
