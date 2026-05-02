export type StudentRecord = {
  id: string
  name: string
  grade: string
  major: string
  identity: string
  email: string
}

export type PolicyRecord = {
  id: string
  title: string
  category: string
  grade: string
  major: string
  channel: string
  publishAt: string
  status: string
}

export type DeliveryLog = {
  id: string
  title: string
  audience: string
  channels: string
  sentAt: string
  count: number
  status: string
}

export type ImportSession = {
  id: string
  fileName: string
  totalRows: number
  successRows: number
  failedRows: number
  importedAt: string
}

export const gradeOptions = ['全部', '2021', '2022', '2023', '2024', '2025']

export const majorOptions = [
  '全部',
  '计算机科学与技术',
  '数据科学与大数据技术',
  '信息安全',
  '人工智能',
]

export const identityOptions = ['全部', '普通学生', '班团骨干', '研究生', '预备党员']

export const categoryOptions = ['奖助', '党建', '竞赛', '就业', '实习', '通知']

export const channelOptions = ['站内消息', '邮件', '导出名单']

export const studentDirectory: StudentRecord[] = [
  {
    id: '20221001',
    name: '张雨晨',
    grade: '2022',
    major: '计算机科学与技术',
    identity: '班团骨干',
    email: '20221001@ruc.edu.cn',
  },
  {
    id: '20221018',
    name: '陈思齐',
    grade: '2022',
    major: '人工智能',
    identity: '普通学生',
    email: '20221018@ruc.edu.cn',
  },
  {
    id: '20231007',
    name: '李明轩',
    grade: '2023',
    major: '数据科学与大数据技术',
    identity: '普通学生',
    email: '20231007@ruc.edu.cn',
  },
  {
    id: '20231021',
    name: '王嘉禾',
    grade: '2023',
    major: '信息安全',
    identity: '预备党员',
    email: '20231021@ruc.edu.cn',
  },
  {
    id: '20241011',
    name: '周若琳',
    grade: '2024',
    major: '人工智能',
    identity: '普通学生',
    email: '20241011@ruc.edu.cn',
  },
  {
    id: '20241022',
    name: '赵天翊',
    grade: '2024',
    major: '计算机科学与技术',
    identity: '班团骨干',
    email: '20241022@ruc.edu.cn',
  },
  {
    id: '20251006',
    name: '何书宁',
    grade: '2025',
    major: '计算机科学与技术',
    identity: '普通学生',
    email: '20251006@ruc.edu.cn',
  },
  {
    id: '20251019',
    name: '彭诗悦',
    grade: '2025',
    major: '数据科学与大数据技术',
    identity: '研究生',
    email: '20251019@ruc.edu.cn',
  },
]

export const initialPolicies: PolicyRecord[] = [
  {
    id: 'policy-001',
    title: '2026 年研究生国奖申报通知',
    category: '奖助',
    grade: '全部',
    major: '全部',
    channel: '站内消息、邮件',
    publishAt: '2026-05-02 09:00',
    status: '待发布',
  },
  {
    id: 'policy-002',
    title: '入党积极分子培训安排',
    category: '党建',
    grade: '2023',
    major: '全部',
    channel: '站内消息',
    publishAt: '2026-05-03 10:30',
    status: '已发布',
  },
  {
    id: 'policy-003',
    title: '信息学院春招实习双选会',
    category: '实习',
    grade: '2022',
    major: '计算机科学与技术',
    channel: '站内消息、导出名单',
    publishAt: '2026-05-04 14:00',
    status: '已发布',
  },
]

export const initialDeliveryLogs: DeliveryLog[] = [
  {
    id: 'delivery-001',
    title: '保研政策答疑会通知',
    audience: '2022 / 计算机科学与技术 / 全部身份',
    channels: '站内消息、邮件',
    sentAt: '2026-05-01 19:30',
    count: 86,
    status: '已发送',
  },
  {
    id: 'delivery-002',
    title: '党员发展材料补交通知',
    audience: '2023 / 全部专业 / 预备党员',
    channels: '站内消息',
    sentAt: '2026-04-30 16:10',
    count: 24,
    status: '已发送',
  },
]

export const initialImportSessions: ImportSession[] = [
  {
    id: 'import-001',
    fileName: '活动通知模板.xlsx',
    totalRows: 25,
    successRows: 24,
    failedRows: 1,
    importedAt: '2026-04-29 15:20',
  },
]
