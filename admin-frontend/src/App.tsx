import { useEffect, useMemo, useState } from 'react'
import * as XLSX from 'xlsx'
import './App.css'
import {
  categoryOptions,
  channelOptions,
  gradeOptions,
  identityOptions,
  majorOptions,
  type DeliveryLog,
  type ImportSession,
  type PolicyRecord,
  type StudentRecord,
} from './mockData'
import { adminApi, type ImportNotificationRow } from './api/adminApi'

function App() {
  const [activeView, setActiveView] = useState<
    'dashboard' | 'import' | 'push' | 'qa'
  >('dashboard')
  const [policies, setPolicies] = useState<PolicyRecord[]>([])
  const [deliveryLogs, setDeliveryLogs] = useState<DeliveryLog[]>([])
  const [importSessions, setImportSessions] = useState<ImportSession[]>([])
  const [previewRecipients, setPreviewRecipients] = useState<StudentRecord[]>([])
  const [teacherName, setTeacherName] = useState('李老师')
  const [loadingText, setLoadingText] = useState('正在连接后端服务...')
  const [dataSourceLabel, setDataSourceLabel] = useState('连接中')
  const [dashboard, setDashboard] = useState({
    pendingNotificationCount: 0,
    targetStudentCount: 0,
    recentDeliveryCount: 0,
    latestImportSuccessRate: 0,
  })
  const [importMessage, setImportMessage] = useState(
    '支持导入 Excel 或 CSV，系统会按“标题、分类、年级、专业、渠道、发布时间、状态”字段解析。',
  )
  const [draftTitle, setDraftTitle] = useState('2026 届实习信息汇总推送')
  const [draftContent, setDraftContent] = useState(
    '请相关同学于本周五前完成岗位志愿填报，未按时提交将影响学院统一推荐。',
  )
  const [selectedGrade, setSelectedGrade] = useState('2022')
  const [selectedMajor, setSelectedMajor] = useState('计算机科学与技术')
  const [selectedIdentity, setSelectedIdentity] = useState('全部')
  const [selectedChannels, setSelectedChannels] = useState<string[]>([
    '站内消息',
    '邮件',
  ])
  const [knowledgeMessage, setKnowledgeMessage] = useState('上传 PDF/TXT/MD 后可重建智能问答知识库。')
  const [curriculumMessage, setCurriculumMessage] = useState('上传 JSON/Excel 培养方案后，学生端学业分析将按最新方案计算。')
  const [knowledgeDocs, setKnowledgeDocs] = useState<
    Array<{ id: string; title: string; fileName: string; uploadedAt?: string }>
  >([])
  const [curriculumSummary, setCurriculumSummary] = useState<{
    fileName: string
    version: string
    programName: string
    requiredModules: number
    requiredCourses: number
    uploadedAt?: string
  } | null>(null)

  useEffect(() => {
    const bootstrap = async () => {
      try {
        const [loginResponse, notificationsResponse, logsResponse, sessionsResponse] =
          await Promise.all([
            adminApi.login({
              username: 'teacher01',
              password: '123456',
            }),
            adminApi.listNotifications(),
            adminApi.listDeliveryLogs(),
            adminApi.listImportSessions(),
          ])

        setTeacherName(loginResponse.data.displayName)
        setPolicies(notificationsResponse.data)
        setDeliveryLogs(logsResponse.data)
        setImportSessions(sessionsResponse.data)
        setLoadingText('已连接到后端真实接口，页面数据来自 Spring Boot 服务。')
        setDataSourceLabel('真实后端接口')

        const [knowledgeResult, curriculumResult] = await Promise.allSettled([
          adminApi.listKnowledgeDocuments(),
          adminApi.getLatestCurriculum(),
        ])

        if (knowledgeResult.status === 'fulfilled') {
          setKnowledgeDocs(knowledgeResult.value.data)
        }

        if (curriculumResult.status === 'fulfilled') {
          setCurriculumSummary(curriculumResult.value.data)
        }
      } catch (error) {
        setLoadingText(
          `后端连接失败：${error instanceof Error ? error.message : '未知错误'}`,
        )
        setDataSourceLabel('后端未连接')
      }
    }

    void bootstrap()
  }, [])

  useEffect(() => {
    const syncPreviewAndDashboard = async () => {
      const filter = {
        grade: selectedGrade,
        major: selectedMajor,
        identity: selectedIdentity,
      }
      try {
        const [previewResponse, dashboardResponse] = await Promise.all([
          adminApi.previewPush(filter),
          adminApi.getDashboard(filter),
        ])

        setPreviewRecipients(previewResponse.data.recipients)
        setDashboard(dashboardResponse.data)
      } catch {
        setPreviewRecipients([])
      }
    }

    void syncPreviewAndDashboard()
  }, [deliveryLogs.length, importSessions.length, selectedGrade, selectedIdentity, selectedMajor])

  const dashboardMetrics = useMemo(
    () => [
      {
        label: '待发布通知',
        value: dashboard.pendingNotificationCount,
        helper: '可通过批量导入后统一审核',
      },
      {
        label: '精准触达对象',
        value: dashboard.targetStudentCount,
        helper: '由年级、专业、身份标签实时计算',
      },
      {
        label: '近 7 日发送任务',
        value: dashboard.recentDeliveryCount,
        helper: '覆盖站内消息、邮件与名单导出',
      },
      {
        label: '最新导入成功率',
        value: `${dashboard.latestImportSuccessRate}%`,
        helper: '导入失败行会在页面直接反馈',
      },
    ],
    [dashboard],
  )

  const toggleChannel = (channel: string) => {
    setSelectedChannels((current) =>
      current.includes(channel)
        ? current.filter((item) => item !== channel)
        : [...current, channel],
    )
  }

  const exportWorkbook = (rows: Record<string, string | number>[], fileName: string) => {
    const worksheet = XLSX.utils.json_to_sheet(rows)
    const workbook = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(workbook, worksheet, 'Sheet1')
    XLSX.writeFile(workbook, fileName)
  }

  const downloadTemplate = () => {
    exportWorkbook(
      [
        {
          标题: '信息学院春季专场招聘会',
          分类: '就业',
          年级: '2022',
          专业: '计算机科学与技术',
          渠道: '站内消息、邮件',
          发布时间: '2026-05-05 14:00',
          状态: '待发布',
        },
      ],
      '信息学院_批量导入模板.xlsx',
    )
  }

  const exportPolicies = () => {
    exportWorkbook(
      policies.map((item) => ({
        标题: item.title,
        分类: item.category,
        年级: item.grade,
        专业: item.major,
        渠道: item.channel,
        发布时间: item.publishAt,
        状态: item.status,
      })),
      '信息学院_通知导出.xlsx',
    )
  }

  const resetMockData = async () => {
    await adminApi.resetMockData()
    const [notificationsResponse, logsResponse, sessionsResponse] = await Promise.all([
      adminApi.listNotifications(),
      adminApi.listDeliveryLogs(),
      adminApi.listImportSessions(),
    ])

    setPolicies(notificationsResponse.data)
    setDeliveryLogs(logsResponse.data)
    setImportSessions(sessionsResponse.data)
    setImportMessage('已从后端重新拉取通知与导入记录。')
  }

  const handleImportFile = async (
    event: React.ChangeEvent<HTMLInputElement>,
  ) => {
    const file = event.target.files?.[0]

    if (!file) {
      return
    }

    try {
      const buffer = await file.arrayBuffer()
      const workbook = XLSX.read(buffer, { type: 'array' })
      const firstSheet = workbook.Sheets[workbook.SheetNames[0]]
      const rawRows = XLSX.utils.sheet_to_json<Record<string, unknown>>(firstSheet, {
        defval: '',
      })

      if (rawRows.length === 0) {
        setImportMessage('导入失败：文件内容为空。')
        return
      }

      const normalizedRows: ImportNotificationRow[] = rawRows.map((row) => ({
        title: String(row['标题'] ?? '').trim(),
        category: String(row['分类'] ?? '').trim(),
        grade: String(row['年级'] ?? '全部').trim() || '全部',
        major: String(row['专业'] ?? '全部').trim() || '全部',
        channel: String(row['渠道'] ?? '站内消息').trim() || '站内消息',
        publishAt: String(row['发布时间'] ?? '').trim() || '待定',
        status: String(row['状态'] ?? '待发布').trim() || '待发布',
      }))

      const importResponse = await adminApi.importNotifications(
        file.name,
        normalizedRows,
      )

      setPolicies(importResponse.data.notifications)
      setImportSessions((current) => [importResponse.data.importSession, ...current])
      setImportMessage(importResponse.message)
      event.target.value = ''
    } catch (error) {
      setImportMessage(
        `导入失败：${error instanceof Error ? error.message : '文件格式无法识别'}`,
      )
    }
  }

  const handleKnowledgeUpload = async (
    event: React.ChangeEvent<HTMLInputElement>,
  ) => {
    const file = event.target.files?.[0]
    if (!file) return
    try {
      const uploadResponse = await adminApi.uploadKnowledgeDocument(file)
      const rebuildResponse = await adminApi.rebuildKnowledgeBase()
      setKnowledgeDocs((current) => [uploadResponse.data, ...current])
      setKnowledgeMessage(
        `${uploadResponse.data.fileName} 上传成功，已重建 ${rebuildResponse.data.chunkCount} 个知识片段。`,
      )
      event.target.value = ''
    } catch (error) {
      setKnowledgeMessage(
        `知识库上传失败：${error instanceof Error ? error.message : '未知错误'}`,
      )
    }
  }

  const handleCurriculumUpload = async (
    event: React.ChangeEvent<HTMLInputElement>,
  ) => {
    const file = event.target.files?.[0]
    if (!file) return
    try {
      const response = await adminApi.uploadCurriculum(file)
      setCurriculumSummary(response.data)
      setCurriculumMessage(
        `${response.data.fileName} 已生效，包含 ${response.data.requiredCourses} 门课程。`,
      )
      event.target.value = ''
    } catch (error) {
      setCurriculumMessage(
        `培养方案上传失败：${error instanceof Error ? error.message : '未知错误'}`,
      )
    }
  }

  const handleSendMessage = async () => {
    if (!draftTitle.trim() || !draftContent.trim()) {
      return
    }

    const response = await adminApi.sendPush({
      title: draftTitle.trim(),
      content: draftContent.trim(),
      grade: selectedGrade,
      major: selectedMajor,
      identity: selectedIdentity,
      channels: selectedChannels,
    })

    setDeliveryLogs((current) => [response.data, ...current])
  }

  const navItems = [
    {
      key: 'dashboard',
      title: '总览',
      description: '查看后台运行概况与待办',
    },
    {
      key: 'import',
      title: '批量导入导出',
      description: '对应 F-14，处理 Excel/CSV 文件',
    },
    {
      key: 'push',
      title: '精准推送',
      description: '对应 F-10 / F-11，按标签触达',
    },
    {
      key: 'qa',
      title: '联调与测试',
      description: '接口约定、Postman 与压测说明',
    },
  ] as const

  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-badge">D</span>
          <div>
            <h1>信息学院管理后台</h1>
            <p>同学 D 交付版</p>
          </div>
        </div>

        <nav className="nav">
          {navItems.map((item) => (
            <button
              key={item.key}
              type="button"
              className={activeView === item.key ? 'nav-item active' : 'nav-item'}
              onClick={() => setActiveView(item.key)}
            >
              <strong>{item.title}</strong>
              <span>{item.description}</span>
            </button>
          ))}
        </nav>

        <section className="sidebar-card">
          <h2>需求覆盖</h2>
          <ul>
            <li>F-10 精准信息推送</li>
            <li>F-11 多渠道通知发送</li>
            <li>F-14 数据导入与导出</li>
          </ul>
        </section>
      </aside>

      <div className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">管理员端 / Vue-React 可替换实现</p>
            <h2>老师用 PC 管理界面原型</h2>
          </div>
          <div className="topbar-meta">
            <span>角色：管理老师 / {teacherName}</span>
            <span>数据源：{dataSourceLabel}</span>
          </div>
        </header>

        <main className="content">
          {activeView === 'dashboard' && (
            <>
              <section className="panel hero-panel">
                <div>
                  <p className="eyebrow">项目摘要</p>
                  <h3>后台侧边栏、导入导出、标签推送已具备演示能力</h3>
                  <p className="muted">
                    {loadingText}
                  </p>
                </div>
                <div className="hero-actions">
                  <button type="button" className="primary-btn" onClick={downloadTemplate}>
                    下载导入模板
                  </button>
                  <button type="button" className="ghost-btn" onClick={exportPolicies}>
                    导出当前通知
                  </button>
                </div>
              </section>

              <section className="metric-grid">
                {dashboardMetrics.map((item) => (
                  <article key={item.label} className="metric-card">
                    <span>{item.label}</span>
                    <strong>{item.value}</strong>
                    <p>{item.helper}</p>
                  </article>
                ))}
              </section>

              <section className="panel two-column">
                <div>
                  <div className="panel-header">
                    <h3>待办事项</h3>
                    <span className="tag">按优先级排序</span>
                  </div>
                  <ul className="todo-list">
                    <li>当前页面默认通过 `/auth` 和 `/admin` 路径访问真实后端。</li>
                    <li>本地开发需要同时启动 `backend` 与 `admin-frontend`。</li>
                    <li>如果页面报 401，请先检查登录接口是否成功返回 `token`。</li>
                    <li>如果知识库或培养方案为空，请检查数据库与后端上传目录。</li>
                  </ul>
                </div>

                <div>
                  <div className="panel-header">
                    <h3>最近发送记录</h3>
                    <span className="tag">实时预览</span>
                  </div>
                  <div className="compact-list">
                    {deliveryLogs.slice(0, 3).map((item) => (
                      <div key={item.id} className="compact-item">
                        <strong>{item.title}</strong>
                        <span>{item.audience}</span>
                        <small>
                          {item.channels} / {item.count} 人 / {item.sentAt}
                        </small>
                      </div>
                    ))}
                  </div>
                </div>
              </section>
            </>
          )}

          {activeView === 'import' && (
            <>
              <section className="panel two-column">
                <div>
                  <div className="panel-header">
                    <h3>批量导入</h3>
                    <span className="tag">F-14</span>
                  </div>
                  <p className="muted">
                    兼容 `.xlsx` 和 `.csv`，页面会读取第一张工作表，并按约定字段自动解析。
                  </p>
                  <label className="upload-box">
                    <input type="file" accept=".xlsx,.xls,.csv" onChange={handleImportFile} />
                    <strong>点击上传 Excel/CSV</strong>
                    <span>{importMessage}</span>
                  </label>
                  <div className="action-row">
                    <button type="button" className="primary-btn" onClick={downloadTemplate}>
                      下载模板
                    </button>
                    <button type="button" className="ghost-btn" onClick={exportPolicies}>
                      导出全部数据
                    </button>
                  </div>
                </div>

                <div>
                  <div className="panel-header">
                    <h3>导入历史</h3>
                    <span className="tag">含成功率</span>
                  </div>
                  <div className="compact-list">
                    {importSessions.map((item) => (
                      <div key={item.id} className="compact-item">
                        <strong>{item.fileName}</strong>
                        <span>
                          共 {item.totalRows} 行，成功 {item.successRows} 行，失败{' '}
                          {item.failedRows} 行
                        </span>
                        <small>{item.importedAt}</small>
                      </div>
                    ))}
                  </div>
                </div>

                <div>
                  <div className="panel-header">
                    <h3>知识库与培养方案</h3>
                    <span className="tag">成员 B</span>
                  </div>
                  <div className="form-stack">
                    <label className="upload-box">
                      <input type="file" accept=".pdf,.txt,.md" onChange={handleKnowledgeUpload} />
                      <strong>上传政策文档到知识库</strong>
                      <span>{knowledgeMessage}</span>
                    </label>
                    <label className="upload-box">
                      <input type="file" accept=".json,.xlsx,.xls" onChange={handleCurriculumUpload} />
                      <strong>上传培养方案文件</strong>
                      <span>{curriculumMessage}</span>
                    </label>
                  </div>
                </div>
              </section>

              <section className="panel">
                <div className="panel-header">
                  <h3>通知数据表</h3>
                  <span className="tag">{policies.length} 条记录</span>
                </div>
                <div className="table-wrap">
                  <table>
                    <thead>
                      <tr>
                        <th>标题</th>
                        <th>分类</th>
                        <th>年级</th>
                        <th>专业</th>
                        <th>渠道</th>
                        <th>发布时间</th>
                        <th>状态</th>
                      </tr>
                    </thead>
                    <tbody>
                      {policies.map((item) => (
                        <tr key={item.id}>
                          <td>{item.title}</td>
                          <td>{item.category}</td>
                          <td>{item.grade}</td>
                          <td>{item.major}</td>
                          <td>{item.channel}</td>
                          <td>{item.publishAt}</td>
                          <td>{item.status}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>

              <section className="panel two-column">
                <div>
                  <div className="panel-header">
                    <h3>知识库文档</h3>
                    <span className="tag">{knowledgeDocs.length} 份</span>
                  </div>
                  <div className="compact-list">
                    {knowledgeDocs.length > 0 ? (
                      knowledgeDocs.map((item) => (
                        <div key={item.id} className="compact-item">
                          <strong>{item.title}</strong>
                          <span>{item.fileName}</span>
                          <small>{item.uploadedAt || '已上传'}</small>
                        </div>
                      ))
                    ) : (
                      <p className="muted">尚未从真实后端读取到知识库文档。</p>
                    )}
                  </div>
                </div>
                <div>
                  <div className="panel-header">
                    <h3>当前培养方案</h3>
                    <span className="tag">{curriculumSummary ? '已生效' : '未配置'}</span>
                  </div>
                  {curriculumSummary ? (
                    <div className="compact-item">
                      <strong>{curriculumSummary.programName}</strong>
                      <span>
                        {curriculumSummary.fileName} / 版本 {curriculumSummary.version}
                      </span>
                      <small>
                        {curriculumSummary.requiredModules} 个模块，{curriculumSummary.requiredCourses} 门课程
                      </small>
                    </div>
                  ) : (
                    <p className="muted">尚未从真实后端读取到培养方案信息。</p>
                  )}
                </div>
              </section>
            </>
          )}

          {activeView === 'push' && (
            <>
              <section className="panel two-column">
                <div>
                  <div className="panel-header">
                    <h3>标签筛选</h3>
                    <span className="tag">F-10</span>
                  </div>
                  <div className="form-grid">
                    <label>
                      年级
                      <select
                        value={selectedGrade}
                        onChange={(event) => setSelectedGrade(event.target.value)}
                      >
                        {gradeOptions.map((item) => (
                          <option key={item} value={item}>
                            {item}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label>
                      专业
                      <select
                        value={selectedMajor}
                        onChange={(event) => setSelectedMajor(event.target.value)}
                      >
                        {majorOptions.map((item) => (
                          <option key={item} value={item}>
                            {item}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label>
                      身份标签
                      <select
                        value={selectedIdentity}
                        onChange={(event) => setSelectedIdentity(event.target.value)}
                      >
                        {identityOptions.map((item) => (
                          <option key={item} value={item}>
                            {item}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label>
                      通知分类
                      <select defaultValue="就业">
                        {categoryOptions.map((item) => (
                          <option key={item} value={item}>
                            {item}
                          </option>
                        ))}
                      </select>
                    </label>
                  </div>

                  <div className="channel-row">
                    {channelOptions.map((channel) => (
                      <label key={channel} className="checkbox-card">
                        <input
                          type="checkbox"
                          checked={selectedChannels.includes(channel)}
                          onChange={() => toggleChannel(channel)}
                        />
                        <span>{channel}</span>
                      </label>
                    ))}
                  </div>
                </div>

                <div>
                  <div className="panel-header">
                    <h3>命中对象预览</h3>
                    <span className="tag">{previewRecipients.length} 人</span>
                  </div>
                  <div className="student-list">
                    {previewRecipients.length > 0 ? (
                      previewRecipients.map((student) => (
                        <div key={student.id} className="student-item">
                          <strong>
                            {student.name} / {student.id}
                          </strong>
                          <span>
                            {student.grade} 级 / {student.major} / {student.identity}
                          </span>
                          <small>{student.email}</small>
                        </div>
                      ))
                    ) : (
                      <p className="muted">当前标签组合没有匹配到学生。</p>
                    )}
                  </div>
                </div>
              </section>

              <section className="panel two-column">
                <div>
                  <div className="panel-header">
                    <h3>消息草稿</h3>
                    <span className="tag">F-11</span>
                  </div>
                  <div className="form-stack">
                    <label>
                      标题
                      <input
                        value={draftTitle}
                        onChange={(event) => setDraftTitle(event.target.value)}
                      />
                    </label>
                    <label>
                      正文
                      <textarea
                        rows={6}
                        value={draftContent}
                        onChange={(event) => setDraftContent(event.target.value)}
                      />
                    </label>
                  </div>
                  <button type="button" className="primary-btn" onClick={handleSendMessage}>
                    发送到匹配对象
                  </button>
                </div>

                <div>
                  <div className="panel-header">
                    <h3>发送日志</h3>
                    <span className="tag">可联调后端</span>
                  </div>
                  <div className="compact-list">
                    {deliveryLogs.map((item) => (
                      <div key={item.id} className="compact-item">
                        <strong>{item.title}</strong>
                        <span>{item.audience}</span>
                        <small>
                          {item.channels} / {item.count} 人 / {item.status}
                        </small>
                      </div>
                    ))}
                  </div>
                </div>
              </section>
            </>
          )}

          {activeView === 'qa' && (
            <>
              <section className="panel two-column">
                <div>
                  <div className="panel-header">
                    <h3>推荐接口</h3>
                    <span className="tag">联调用</span>
                  </div>
                  <div className="api-list">
                    <code>GET /admin/notifications</code>
                    <code>POST /admin/import/notifications</code>
                    <code>GET /admin/export/notifications</code>
                    <code>POST /admin/push/preview</code>
                    <code>POST /admin/push/send</code>
                  </div>
                </div>

                <div>
                  <div className="panel-header">
                    <h3>联调说明</h3>
                    <span className="tag">给后端 A</span>
                  </div>
                  <ul className="todo-list">
                    <li>统一返回 `{`}"success/message/data"{`}` 结构。</li>
                    <li>推送预览接口返回命中学生列表与总人数。</li>
                    <li>导入接口返回成功行、失败行、失败原因和导入批次信息。</li>
                    <li>发送接口需记录管理员操作日志，满足审计要求。</li>
                  </ul>
                </div>
              </section>

              <section className="panel">
                <div className="panel-header">
                  <h3>测试建议</h3>
                  <span className="tag">Postman + k6</span>
                </div>
                <ul className="todo-list">
                  <li>Postman：覆盖登录、推送预览、推送发送、导入导出四类接口。</li>
                  <li>压力测试：模拟 50~200 并发请求，重点观察标签筛选与消息发送耗时。</li>
                  <li>异常测试：空标题、空分类、非法文件格式、零匹配对象等边界情况。</li>
                  <li>安全测试：验证非管理员角色不可调用 `/admin/*` 路径。</li>
                </ul>
                <div className="action-row action-row-top">
                  <button type="button" className="ghost-btn" onClick={resetMockData}>
                    重置演示数据
                  </button>
                </div>
              </section>
            </>
          )}
        </main>
      </div>
    </div>
  )
}

export default App
