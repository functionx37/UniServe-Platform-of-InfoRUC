import { adminApi, request, uploadFile } from './adminApi.mock'

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

export const adminApiExtended = {
  ...adminApi,

  async listKnowledgeDocuments() {
    return request<KnowledgeDocumentItem[]>('/admin/knowledge/documents', {
      method: 'GET',
    })
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

  async listApplications(status: string = '全部') {
    return request<any[]>(`/admin/applications?status=${encodeURIComponent(status)}`, {
      method: 'GET',
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
}

export { adminApiExtended as adminApi }
