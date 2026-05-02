import {
  initialDeliveryLogs,
  initialImportSessions,
  initialPolicies,
  studentDirectory,
  type DeliveryLog,
  type ImportSession,
  type PolicyRecord,
  type StudentRecord,
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

type DatabaseShape = {
  notifications: PolicyRecord[]
  deliveryLogs: DeliveryLog[]
  importSessions: ImportSession[]
  students: StudentRecord[]
}

const STORAGE_KEY = 'rucapp-admin-database'

let memoryDatabase: DatabaseShape | null = null

const clone = <T,>(value: T): T => JSON.parse(JSON.stringify(value)) as T

const defaultDatabase = (): DatabaseShape => ({
  notifications: clone(initialPolicies),
  deliveryLogs: clone(initialDeliveryLogs),
  importSessions: clone(initialImportSessions),
  students: clone(studentDirectory),
})

const hasLocalStorage = () =>
  typeof window !== 'undefined' && typeof window.localStorage !== 'undefined'

const readDatabase = (): DatabaseShape => {
  if (hasLocalStorage()) {
    const raw = window.localStorage.getItem(STORAGE_KEY)

    if (raw) {
      return JSON.parse(raw) as DatabaseShape
    }

    const initial = defaultDatabase()
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(initial))
    return initial
  }

  if (memoryDatabase === null) {
    memoryDatabase = defaultDatabase()
  }

  return memoryDatabase
}

const writeDatabase = (database: DatabaseShape) => {
  if (hasLocalStorage()) {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(database))
    return
  }

  memoryDatabase = database
}

const delay = async <T,>(data: ApiResponse<T>, ms = 180) => {
  await new Promise((resolve) => {
    window.setTimeout(resolve, ms)
  })
  return data
}

const matchRecipients = (
  students: StudentRecord[],
  filter: PushPreviewRequest,
) => {
  return students.filter((student) => {
    const gradeMatch =
      filter.grade === '全部' || student.grade === filter.grade
    const majorMatch =
      filter.major === '全部' || student.major === filter.major
    const identityMatch =
      filter.identity === '全部' || student.identity === filter.identity

    return gradeMatch && majorMatch && identityMatch
  })
}

export const adminApi = {
  async login(payload: LoginRequest) {
    return delay<LoginResponse>({
      success: true,
      message: '登录成功',
      data: {
        token: `mock-token-${payload.username}`,
        role: '管理老师',
        displayName: payload.username === 'teacher01' ? '李老师' : '管理员',
      },
    })
  },

  async getDashboard(filter: PushPreviewRequest) {
    const database = readDatabase()
    const latest = database.importSessions[0]
    const preview = matchRecipients(database.students, filter)

    return delay<DashboardResponse>({
      success: true,
      message: '获取仪表盘数据成功',
      data: {
        pendingNotificationCount: database.notifications.filter(
          (item) => item.status === '待发布',
        ).length,
        targetStudentCount: preview.length,
        recentDeliveryCount: database.deliveryLogs.length,
        latestImportSuccessRate:
          latest === undefined || latest.totalRows === 0
            ? 0
            : Math.round((latest.successRows / latest.totalRows) * 100),
      },
    })
  },

  async listNotifications() {
    const database = readDatabase()
    return delay<PolicyRecord[]>({
      success: true,
      message: '获取通知列表成功',
      data: database.notifications,
    })
  },

  async listDeliveryLogs() {
    const database = readDatabase()
    return delay<DeliveryLog[]>({
      success: true,
      message: '获取发送日志成功',
      data: database.deliveryLogs,
    })
  },

  async listImportSessions() {
    const database = readDatabase()
    return delay<ImportSession[]>({
      success: true,
      message: '获取导入历史成功',
      data: database.importSessions,
    })
  },

  async previewPush(payload: PushPreviewRequest) {
    const database = readDatabase()
    const recipients = matchRecipients(database.students, payload)

    return delay<PushPreviewResponse>({
      success: true,
      message: '获取推送预览成功',
      data: {
        recipients,
        total: recipients.length,
      },
    })
  },

  async sendPush(payload: SendPushRequest) {
    const database = readDatabase()
    const recipients = matchRecipients(database.students, payload)
    const newLog: DeliveryLog = {
      id: `delivery-${Date.now()}`,
      title: payload.title,
      audience: `${payload.grade} / ${payload.major} / ${payload.identity}`,
      channels: payload.channels.join('、') || '未选择渠道',
      sentAt: new Date().toLocaleString('zh-CN', { hour12: false }),
      count: recipients.length,
      status: recipients.length > 0 ? '已发送' : '无匹配对象',
    }

    const nextDatabase: DatabaseShape = {
      ...database,
      deliveryLogs: [newLog, ...database.deliveryLogs],
    }
    writeDatabase(nextDatabase)

    return delay<DeliveryLog>({
      success: true,
      message: '通知发送成功',
      data: newLog,
    })
  },

  async importNotifications(fileName: string, rows: ImportNotificationRow[]) {
    const database = readDatabase()
    const validRows: PolicyRecord[] = []
    let failedRows = 0

    rows.forEach((row, index) => {
      if (!row.title.trim() || !row.category.trim()) {
        failedRows += 1
        return
      }

      validRows.push({
        id: `policy-${Date.now()}-${index}`,
        title: row.title.trim(),
        category: row.category.trim(),
        grade: row.grade.trim() || '全部',
        major: row.major.trim() || '全部',
        channel: row.channel.trim() || '站内消息',
        publishAt: row.publishAt.trim() || '待定',
        status: row.status.trim() || '待发布',
      })
    })

    const session: ImportSession = {
      id: `import-${Date.now()}`,
      fileName,
      totalRows: rows.length,
      successRows: validRows.length,
      failedRows,
      importedAt: new Date().toLocaleString('zh-CN', { hour12: false }),
    }

    const nextDatabase: DatabaseShape = {
      ...database,
      notifications: [...validRows, ...database.notifications],
      importSessions: [session, ...database.importSessions],
    }
    writeDatabase(nextDatabase)

    return delay<{
      importSession: ImportSession
      notifications: PolicyRecord[]
    }>({
      success: true,
      message: `已导入 ${validRows.length} 行，失败 ${failedRows} 行`,
      data: {
        importSession: session,
        notifications: nextDatabase.notifications,
      },
    })
  },

  async resetMockData() {
    const database = defaultDatabase()
    writeDatabase(database)

    return delay<boolean>({
      success: true,
      message: '演示数据已重置',
      data: true,
    })
  },
}
