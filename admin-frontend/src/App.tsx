import { useEffect, useState } from 'react'
import * as XLSX from 'xlsx'
import './App.css'
import {
  channelOptions,
  gradeOptions,
  identityOptions,
  majorOptions,
} from './mockData'
import { adminApi } from './api/adminApi'

type ViewType = 'dashboard' | 'notifications' | 'push' | 'applications' | 'users' | 'knowledge' | 'curriculum'

function App() {
  const [isLoggedIn, setIsLoggedIn] = useState<boolean>(!!sessionStorage.getItem('token'))
  const [activeView, setActiveView] = useState<ViewType>('dashboard')
  const [teacherName, setTeacherName] = useState('加载中...')
  const [loading, setLoading] = useState(isLoggedIn)
  
  // Login Form States
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loginError, setLoginError] = useState('')

  // Data States
  const [dashboard, setDashboard] = useState({
    pendingNotificationCount: 0,
    targetStudentCount: 0,
    recentDeliveryCount: 0,
    latestImportSuccessRate: 0,
  })
  const [notifications, setNotifications] = useState<any[]>([])
  const [deliveryLogs, setDeliveryLogs] = useState<any[]>([])
  const [applications, setApplications] = useState<any[]>([])
  const [knowledgeDocs, setKnowledgeDocs] = useState<any[]>([])
  const [curriculum, setCurriculum] = useState<any>(null)
  const [users, setUsers] = useState<any[]>([])
  const [userSearch, setUserSearch] = useState('')
  const [userFilter, setUserFilter] = useState({ roleId: 4, grade: '全部', major: '全部' })
  
  // Form/UI States
  const [selectedGrade, setSelectedGrade] = useState('全部')
  const [selectedMajor, setSelectedMajor] = useState('全部')
  const [selectedIdentity, setSelectedIdentity] = useState('全部')
  const [selectedChannels, setSelectedChannels] = useState<string[]>(['站内消息'])
  const [pushTitle, setPushTitle] = useState('')
  const [pushContent, setPushContent] = useState('')
  const [previewRecipients, setPreviewRecipients] = useState<any[]>([])
  const [isSyncing, setIsSyncing] = useState(false)
  const [selectedApp, setSelectedApp] = useState<any>(null)
  const [isAppModalOpen, setIsAppModalOpen] = useState(false)
  const [selectedUser, setSelectedUser] = useState<any>(null)
  const [isUserModalOpen, setIsUserModalOpen] = useState(false)
  const [isAddUserModalOpen, setIsAddUserModalOpen] = useState(false)
  const [newUser, setNewUser] = useState({
    username: '',
    realName: '',
    studentNo: '',
    grade: '2023级',
    major: '计算机科学与技术',
    identity: '普通学生',
    email: '',
    phone: '',
    idCard: ''
  })

  // Initialization
  useEffect(() => {
    if (isLoggedIn) {
      initApp()
    }
  }, [isLoggedIn])

  const initApp = async () => {
    setLoading(true)
    try {
      // 验证当前 token 并获取用户信息
      const res = await adminApi.listUsers({ keyword: 'teacher01' }) // 简单示例：获取管理员自身信息
      const admin = res.data.find((u: any) => u.username === 'teacher01')
      if (admin) {
        setTeacherName(admin.realName)
      } else {
        setTeacherName('管理老师')
      }
      await refreshAll()
    } catch (err) {
      console.error('Init failed', err)
      // 如果初始化失败（如 token 过期），跳转登录
      setIsLoggedIn(false)
      sessionStorage.removeItem('token')
    } finally {
      setLoading(false)
    }
  }

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoginError('')
    try {
      await adminApi.login({ username, password })
      setIsLoggedIn(true)
    } catch (err: any) {
      setLoginError(err.message || '登录失败，请检查账号密码')
    }
  }

  const handleLogout = () => {
    sessionStorage.removeItem('token')
    setIsLoggedIn(false)
    setTeacherName('加载中...')
  }

  const refreshAll = async () => {
    setIsSyncing(true)
    try {
      const [dashRes, notifyRes, logsRes, appsRes, docsRes, currRes, userRes] = await Promise.all([
        adminApi.getDashboard({ grade: selectedGrade, major: selectedMajor, identity: selectedIdentity }),
        adminApi.listNotifications(),
        adminApi.listDeliveryLogs(),
        adminApi.listApplications('全部'),
        adminApi.listKnowledgeDocuments(),
        adminApi.getLatestCurriculum(),
        adminApi.listUsers({ ...userFilter, keyword: userSearch }),
      ])
      setDashboard(dashRes.data)
      setNotifications(notifyRes.data)
      setDeliveryLogs(logsRes.data)
      setApplications(appsRes.data)
      setKnowledgeDocs(docsRes.data)
      setCurriculum(currRes.data)
      setUsers(userRes.data)
    } catch (err) {
      console.error('Refresh failed', err)
    } finally {
      setIsSyncing(false)
    }
  }

  const refreshUsers = async () => {
    try {
      const res = await adminApi.listUsers({ ...userFilter, keyword: userSearch })
      setUsers(res.data)
    } catch (err) {
      console.error('Fetch users failed', err)
    }
  }

  useEffect(() => {
    if (activeView === 'users') {
      refreshUsers()
    }
  }, [activeView, userFilter, userSearch])

  // Push Preview Sync
  useEffect(() => {
    if (!isLoggedIn || activeView !== 'push') return
    const syncPreview = async () => {
      try {
        const res = await adminApi.previewPush({ grade: selectedGrade, major: selectedMajor, identity: selectedIdentity })
        setPreviewRecipients(res.data.recipients)
      } catch (err) {
        setPreviewRecipients([])
      }
    }
    syncPreview()
  }, [isLoggedIn, activeView, selectedGrade, selectedMajor, selectedIdentity])

  // Handlers
  const handlePush = async () => {
    if (!pushTitle || !pushContent) return alert('请填写标题和内容')
    try {
      await adminApi.sendPush({
        title: pushTitle,
        content: pushContent,
        grade: selectedGrade,
        major: selectedMajor,
        identity: selectedIdentity,
        channels: selectedChannels,
      })
      alert('推送成功')
      setPushTitle('')
      setPushContent('')
      refreshAll()
    } catch (err: any) {
      alert('推送失败: ' + err.message)
    }
  }

  const handleAudit = async (id: number, action: 'pass' | 'reject') => {
    const opinion = prompt('请输入审批意见（可选）') || ''
    try {
      await adminApi.auditApplication(id, action, opinion)
      alert('操作成功')
      if (selectedApp && selectedApp.id === id) {
        handleViewDetail(id) // 刷新详情
      }
      refreshAll()
    } catch (err: any) {
      alert('操作失败: ' + err.message)
    }
  }

  const handleViewDetail = async (id: number) => {
    try {
      const res = await adminApi.getApplicationDetail(id)
      setSelectedApp(res.data)
      setIsAppModalOpen(true)
    } catch (err: any) {
      alert('获取详情失败: ' + err.message)
    }
  }

  const handleDeleteApplication = async (id: number) => {
    if (!confirm('确定要删除这条申请记录吗？')) return
    try {
      await adminApi.deleteApplication(id)
      refreshAll()
    } catch (err: any) {
      alert('删除失败: ' + err.message)
    }
  }

  const handleDeleteNotification = async (id: string) => {
    if (!confirm('确定要删除这条通知吗？')) return
    try {
      await adminApi.deleteNotification(id)
      refreshAll()
    } catch (err: any) {
      alert('删除失败: ' + err.message)
    }
  }

  const handleUpdateNotificationStatus = async (id: string, currentStatus: string) => {
    const nextStatus = currentStatus === '已发布' ? '已下线' : '已发布'
    try {
      await adminApi.updateNotificationStatus(id, nextStatus)
      refreshAll()
    } catch (err: any) {
      alert('更新失败: ' + err.message)
    }
  }

  const handleDeleteKnowledge = async (id: string) => {
    if (!confirm('确定要删除这个文档吗？')) return
    try {
      await adminApi.deleteKnowledgeDocument(id)
      refreshAll()
    } catch (err: any) {
      alert('删除失败: ' + err.message)
    }
  }

  const handleDeleteCurriculum = async (id: string) => {
    if (!confirm('确定要删除这个培养方案吗？')) return
    try {
      await adminApi.deleteCurriculum(id)
      refreshAll()
    } catch (err: any) {
      alert('删除失败: ' + err.message)
    }
  }

  const handleDeletePushLog = async (id: string) => {
    if (!confirm('确定要删除这条推送记录吗？')) return
    try {
      await adminApi.deleteDeliveryLog(id)
      refreshAll()
    } catch (err: any) {
      alert('删除失败: ' + err.message)
    }
  }

  const handleDeleteUser = async (id: number) => {
    if (!confirm('确定要注销此学生档案吗？')) return
    try {
      await adminApi.deleteUser(id)
      refreshUsers()
    } catch (err: any) {
      alert('删除失败: ' + err.message)
    }
  }

  const handleViewUserDetail = async (user: any) => {
    setSelectedUser(user)
    setIsUserModalOpen(true)
  }

  const handleResetPassword = async (id: number) => {
    if (!confirm('确定要将该学生的密码重置为 123456 吗？')) return
    try {
      await adminApi.updateUser(id, { password: '123456' })
      alert('密码已重置为 123456')
    } catch (err: any) {
      alert('重置失败: ' + err.message)
    }
  }

  const handleAddUser = async (e: React.FormEvent) => {
    e.preventDefault()
    try {
      // 自动设置用户名（如果为空则使用学号）
      const payload = { 
        ...newUser, 
        username: newUser.username || newUser.studentNo,
        roleId: 4 // 固定为学生角色
      }
      await adminApi.createUser(payload)
      alert('学生档案录入成功')
      setIsAddUserModalOpen(false)
      setNewUser({
        username: '', realName: '', studentNo: '', 
        grade: '2023级', major: '计算机科学与技术', identity: '普通学生',
        email: '', phone: '', idCard: ''
      })
      refreshUsers()
    } catch (err: any) {
      alert('录入失败: ' + err.message)
    }
  }

  const handleUserImport = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    try {
      const buffer = await file.arrayBuffer()
      const wb = XLSX.read(buffer, { type: 'array', cellDates: true })
      const data = XLSX.utils.sheet_to_json<any>(wb.Sheets[wb.SheetNames[0]])
      
      const formatValue = (val: any) => {
        if (val instanceof Date) {
          const y = val.getFullYear();
          const m = String(val.getMonth() + 1).padStart(2, '0');
          const d = String(val.getDate()).padStart(2, '0');
          return `${y}-${m}-${d}`;
        }
        return String(val || '').trim();
      };

      const rows = data.map(r => ({
        username: formatValue(r['学号'] || r['用户名']),
        realName: formatValue(r['姓名']),
        studentNo: formatValue(r['学号']),
        grade: formatValue(r['年级'] || '2023级'),
        major: formatValue(r['专业'] || '计算机科学与技术'),
        identity: formatValue(r['身份'] || '普通学生'),
        email: formatValue(r['邮箱']),
        phone: formatValue(r['手机号']),
        roleId: 4
      }))
      
      await adminApi.importUsers(rows)
      alert('批量导入指令已发送')
      refreshUsers()
    } catch (err: any) {
      alert('导入失败: ' + err.message)
    }
  }

  const handleImport = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    try {
      const buffer = await file.arrayBuffer()
      // cellDates: true 确保日期列被解析为 JS Date 对象而非 Excel 数字
      const wb = XLSX.read(buffer, { type: 'array', cellDates: true })
      const data = XLSX.utils.sheet_to_json<any>(wb.Sheets[wb.SheetNames[0]])
      
      const formatValue = (val: any) => {
        if (val instanceof Date) {
          // 格式化为 yyyy-MM-dd HH:mm
          const y = val.getFullYear();
          const m = String(val.getMonth() + 1).padStart(2, '0');
          const d = String(val.getDate()).padStart(2, '0');
          const hh = String(val.getHours()).padStart(2, '0');
          const mm = String(val.getMinutes()).padStart(2, '0');
          return `${y}-${m}-${d} ${hh}:${mm}`;
        }
        return String(val || '').trim();
      };

      const rows = data.map(r => ({
        title: formatValue(r['标题'] || r['title']),
        category: formatValue(r['分类'] || r['category']),
        grade: formatValue(r['年级'] || r['grade'] || '全部'),
        major: formatValue(r['专业'] || r['major'] || '全部'),
        channel: formatValue(r['渠道'] || r['channel'] || '站内消息'),
        status: formatValue(r['状态'] || r['status'] || '待发布'),
        content: formatValue(r['内容'] || r['content']),
        links: formatValue(r['链接'] || r['links'] || '[]'),
      }))
      
      const res = await adminApi.importNotifications(file.name, rows)
      alert(res.message)
      refreshAll()
    } catch (err: any) {
      alert('导入失败: ' + err.message)
    }
  }

  const handleKnowledgeUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    try {
      await adminApi.uploadKnowledgeDocument(file)
      // 立即刷新列表，让用户看到文件已上传
      await refreshAll()
      // 后台异步重建索引
      adminApi.rebuildKnowledgeBase().catch(err => console.error('Rebuild failed', err))
      alert('上传成功，系统正在后台进行 AI 向量化分析...')
    } catch (err: any) {
      alert('上传失败: ' + err.message)
    }
  }

  const handleBootstrapKnowledge = async () => {
    if (!confirm('确定要从服务器 file 目录初始化知识库吗？')) return
    try {
      await adminApi.bootstrapKnowledgeBase()
      alert('知识库初始化指令已发送，请稍后刷新。')
      refreshAll()
    } catch (err: any) {
      alert('初始化失败: ' + err.message)
    }
  }

  const handleCurriculumUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    try {
      await adminApi.uploadCurriculum(file)
      alert('培养方案更新成功')
      refreshAll()
    } catch (err: any) {
      alert('上传失败: ' + err.message)
    }
  }

  // 渲染登录页
  if (!isLoggedIn) {
    return (
      <div className="login-page">
        <div className="login-card">
          <div className="login-header">
            <div className="login-logo">U</div>
            <h1>UniServe Admin</h1>
            <p>管理老师登录中心</p>
          </div>
          <form onSubmit={handleLogin}>
            <div className="form-group">
              <label>管理账号</label>
              <input 
                type="text" 
                className="form-control" 
                placeholder="请输入账号" 
                value={username} 
                onChange={e => setUsername(e.target.value)}
                required
              />
            </div>
            <div className="form-group">
              <label>登录密码</label>
              <input 
                type="password" 
                className="form-control" 
                placeholder="请输入密码" 
                value={password} 
                onChange={e => setPassword(e.target.value)}
                required
              />
            </div>
            {loginError && <div style={{ color: 'var(--danger)', fontSize: '12px', marginBottom: '16px' }}>{loginError}</div>}
            <button type="submit" className="btn btn-primary" style={{ width: '100%', padding: '12px' }}>进入管理系统</button>
          </form>
          <div style={{ marginTop: '24px', textAlign: 'center', fontSize: '12px', color: 'var(--muted)' }}>
            人大信息学院 / 综合服务平台
          </div>
        </div>
      </div>
    )
  }

  if (loading) return <div className="loading-overlay"><div className="spinner"></div></div>

  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-badge">U</div>
          <div>
            <h1>UniServe Admin</h1>
            <p>学生事务综合服务平台</p>
          </div>
        </div>

        <nav className="nav">
          <NavItem active={activeView === 'dashboard'} onClick={() => setActiveView('dashboard')} icon="📊" text="工作台总览" />
          <NavItem active={activeView === 'notifications'} onClick={() => setActiveView('notifications')} icon="📢" text="通知公告管理" />
          <NavItem active={activeView === 'push'} onClick={() => setActiveView('push')} icon="🎯" text="精准信息推送" />
          <NavItem active={activeView === 'applications'} onClick={() => setActiveView('applications')} icon="📝" text="事务审批中心" />
          <NavItem active={activeView === 'users'} onClick={() => setActiveView('users')} icon="👥" text="学生档案管理" />
          <NavItem active={activeView === 'knowledge'} onClick={() => setActiveView('knowledge')} icon="🧠" text="AI 知识库维护" />
          <NavItem active={activeView === 'curriculum'} onClick={() => setActiveView('curriculum')} icon="🎓" text="培养方案管理" />
        </nav>

        <div style={{ marginTop: 'auto', padding: '16px', fontSize: '12px', opacity: 0.5 }}>
          v1.1.0-Release / InfoRUC
        </div>
      </aside>

      <div className="workspace">
        <header className="topbar">
          <div className="topbar-title">
            <h2>{getViewTitle(activeView)}</h2>
          </div>
          <div className="topbar-user">
            {isSyncing && <span style={{ fontSize: '12px', color: 'var(--primary)' }}>同步中...</span>}
            <div className="user-info">
              <span className="user-name">{teacherName}</span>
              <span className="user-role">管理老师</span>
            </div>
            <button className="btn btn-ghost" onClick={handleLogout}>🚪 退出</button>
            <button className="btn btn-ghost" onClick={refreshAll}>🔄 刷新</button>
          </div>
        </header>

        <main className="content">
          {activeView === 'dashboard' && (
            <>
              <div className="card-grid">
                <StatCard label="待审事务" value={applications.filter(a => String(a.status) === '待审核' || a.status === 0).length} footer="需要尽快处理" />
                <StatCard label="待发通知" value={dashboard.pendingNotificationCount} footer="已导入待审核" />
                <StatCard label="活跃学生" value={dashboard.targetStudentCount} footer="当前标签命中数" />
                <StatCard label="导入成功率" value={`${dashboard.latestImportSuccessRate}%`} footer="最近一次批次" />
              </div>

              <div className="grid">
                <div className="panel">
                  <div className="panel-header">
                    <h3>最近申请</h3>
                    <button className="btn btn-ghost" onClick={() => setActiveView('applications')}>更多</button>
                  </div>
                  <div className="table-container">
                    <table>
                      <thead><tr><th>申请人</th><th>类别</th><th>状态</th></tr></thead>
                      <tbody>
                        {applications.slice(0, 5).map(app => (
                          <tr key={app.id}>
                            <td>{app.userName || '未知'}</td>
                            <td>{app.typeLabel}</td>
                            <td><StatusBadge status={app.status} /></td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
                <div className="panel">
                  <div className="panel-header">
                    <h3>推送日志</h3>
                    <button className="btn btn-ghost" onClick={() => setActiveView('push')}>更多</button>
                  </div>
                  <div className="table-container">
                    <table>
                      <thead><tr><th>标题</th><th>对象</th><th>时间</th><th>操作</th></tr></thead>
                      <tbody>
                        {deliveryLogs.slice(0, 10).map(log => (
                          <tr key={log.id}>
                            <td>{log.title}</td>
                            <td>{log.audience?.split('/')[0] || '全体'}</td>
                            <td>{log.sentAt?.split(' ')[0] || '-'}</td>
                            <td>
                              <button className="btn btn-ghost" style={{ padding: '4px 8px', fontSize: '12px', color: 'var(--danger)' }} onClick={() => handleDeletePushLog(log.id)}>删除</button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
            </>
          )}

          {activeView === 'notifications' && (
            <div className="panel">
              <div className="panel-header">
                <h3>通知公告列表</h3>
                <div style={{ display: 'flex', gap: '12px' }}>
                  <button 
                    className="btn btn-ghost" 
                    onClick={() => adminApi.downloadFile(adminApi.getTemplateDownloadUrl('notifications'), 'notifications_import_template.xlsx')}
                    style={{ display: 'flex', alignItems: 'center' }}
                  >
                    📄 下载模板
                  </button>
                  <label className="btn btn-primary">
                    📤 批量导入
                    <input type="file" style={{ display: 'none' }} onChange={handleImport} accept=".xlsx,.csv" />
                  </label>
                </div>
              </div>
              <div className="table-container">
                <table>
                  <thead>
                    <tr><th>标题</th><th>分类</th><th>范围</th><th>发布时间</th><th>状态</th><th>操作</th></tr>
                  </thead>
                  <tbody>
                    {notifications.map(n => (
                      <tr key={n.id}>
                        <td>{n.title}</td>
                        <td><span className="badge badge-info">{n.category}</span></td>
                        <td>{n.grade} / {n.major}</td>
                        <td>{n.publishAt}</td>
                        <td><span className={`badge ${n.status === '已发布' ? 'badge-success' : 'badge-warning'}`}>{n.status}</span></td>
                        <td>
                          <div style={{ display: 'flex', gap: '8px' }}>
                            <button 
                              className="btn btn-ghost" 
                              style={{ padding: '4px 8px', fontSize: '12px', color: n.status === '已发布' ? 'var(--warning)' : 'var(--success)' }} 
                              onClick={() => handleUpdateNotificationStatus(n.id, n.status)}
                            >
                              {n.status === '已发布' ? '下线' : '发布'}
                            </button>
                            <button className="btn btn-ghost" style={{ padding: '4px 8px', fontSize: '12px', color: 'var(--danger)' }} onClick={() => handleDeleteNotification(n.id)}>删除</button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {activeView === 'push' && (
            <div className="grid" style={{ gridTemplateColumns: '1fr 1.5fr' }}>
              <div className="panel">
                <div className="panel-header"><h3>发送配置</h3></div>
                <div className="form-group">
                  <label>目标年级</label>
                  <select className="form-control" value={selectedGrade} onChange={e => setSelectedGrade(e.target.value)}>
                    {gradeOptions.map(o => <option key={o} value={o}>{o}</option>)}
                  </select>
                </div>
                <div className="form-group">
                  <label>目标专业</label>
                  <select className="form-control" value={selectedMajor} onChange={e => setSelectedMajor(e.target.value)}>
                    {majorOptions.map(o => <option key={o} value={o}>{o}</option>)}
                  </select>
                </div>
                <div className="form-group">
                  <label>身份类型</label>
                  <select className="form-control" value={selectedIdentity} onChange={e => setSelectedIdentity(e.target.value)}>
                    {identityOptions.map(o => <option key={o} value={o}>{o}</option>)}
                  </select>
                </div>
                <div className="form-group">
                  <label>推送渠道</label>
                  <div style={{ display: 'flex', gap: '10px' }}>
                    {channelOptions.map(c => (
                      <label key={c} style={{ fontSize: '14px' }}>
                        <input type="checkbox" checked={selectedChannels.includes(c)} onChange={e => {
                          if (e.target.checked) setSelectedChannels([...selectedChannels, c])
                          else setSelectedChannels(selectedChannels.filter(i => i !== c))
                        }} /> {c}
                      </label>
                    ))}
                  </div>
                </div>
                <hr style={{ border: 'none', borderTop: '1px solid var(--border)', margin: '20px 0' }} />
                <div className="form-group">
                  <label>消息标题</label>
                  <input className="form-control" value={pushTitle} onChange={e => setPushTitle(e.target.value)} placeholder="请输入通知标题" />
                </div>
                <div className="form-group">
                  <label>消息正文</label>
                  <textarea className="form-control" value={pushContent} onChange={e => setPushContent(e.target.value)} rows={5} placeholder="请输入详细内容..." />
                </div>
                <button className="btn btn-primary" style={{ width: '100%' }} onClick={handlePush}>立即发送到 {previewRecipients.length} 人</button>
              </div>
              
              <div className="panel">
                <div className="panel-header"><h3>命中学生预览 ({previewRecipients.length})</h3></div>
                <div className="table-container" style={{ maxHeight: '600px' }}>
                  <table>
                    <thead><tr><th>学号</th><th>姓名</th><th>专业</th></tr></thead>
                    <tbody>
                      {previewRecipients.map(s => (
                        <tr key={s.id}><td>{s.id}</td><td>{s.name}</td><td>{s.major}</td></tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {activeView === 'applications' && (
            <div className="panel">
              <div className="panel-header"><h3>事务审批中心</h3></div>
              <div className="table-container">
                <table>
                  <thead>
                    <tr><th>申请编号</th><th>申请人</th><th>事务类型</th><th>提交时间</th><th>状态</th><th>操作</th></tr>
                  </thead>
                  <tbody>
                    {applications.map(app => (
                      <tr key={app.id}>
                        <td>#{app.id}</td>
                        <td>{app.userName || '-'}</td>
                        <td>{app.typeLabel}</td>
                        <td>{app.createdAt || '-'}</td>
                        <td><StatusBadge status={app.status} /></td>
                        <td>
                          <div style={{ display: 'flex', gap: '8px' }}>
                            {(String(app.status) === '待审核' || app.status === 0) && (
                              <>
                                <button className="btn btn-ghost" style={{ padding: '4px 8px', fontSize: '12px', color: 'var(--success)' }} onClick={() => handleAudit(app.id, 'pass')}>通过</button>
                                <button className="btn btn-ghost" style={{ padding: '4px 8px', fontSize: '12px', color: 'var(--danger)' }} onClick={() => handleAudit(app.id, 'reject')}>驳回</button>
                              </>
                            )}
                            <button className="btn btn-ghost" style={{ padding: '4px 8px', fontSize: '12px', color: 'var(--primary)' }} onClick={() => handleViewDetail(app.id)}>详情</button>
                            <button className="btn btn-ghost" style={{ padding: '4px 8px', fontSize: '12px', color: 'var(--danger)' }} onClick={() => handleDeleteApplication(app.id)}>删除</button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {activeView === 'knowledge' && (
            <div className="grid">
              <div className="panel">
                <div className="panel-header"><h3>知识库文档管理</h3></div>
                <div className="table-container">
                  <table>
                    <thead><tr><th>标题</th><th>文件名</th><th>状态</th><th>操作</th></tr></thead>
                    <tbody>
                      {knowledgeDocs.map(doc => (
                        <tr key={doc.id}>
                          <td>{doc.title}</td>
                          <td>{doc.fileName}</td>
                          <td><span className="badge badge-success">已索引</span></td>
                          <td>
                            <button className="btn btn-ghost" style={{ padding: '4px 8px', fontSize: '12px', color: 'var(--danger)' }} onClick={() => handleDeleteKnowledge(doc.id)}>删除</button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
              <div className="panel">
                <div className="panel-header"><h3>维护操作</h3></div>
                <div className="form-group">
                  <label>上传本地文档 (.pdf, .txt, .md)</label>
                  <label className="upload-area" style={{ display: 'block' }}>
                    <input type="file" onChange={handleKnowledgeUpload} />
                    <div style={{ fontSize: '24px', marginBottom: '8px' }}>📁</div>
                    <strong>点击或拖拽上传</strong>
                    <p style={{ fontSize: '12px', color: 'var(--muted)' }}>上传后会自动进行文本切片与向量化</p>
                  </label>
                </div>
                <hr style={{ border: 'none', borderTop: '1px solid var(--border)', margin: '20px 0' }} />
                <div className="form-group">
                  <label>服务器目录同步</label>
                  <p style={{ fontSize: '14px', marginBottom: '12px' }}>扫描服务器 <code>file/</code> 目录下的官方文件并同步到知识库。</p>
                  <button className="btn btn-ghost" style={{ width: '100%' }} onClick={handleBootstrapKnowledge}>🚀 同步 file/ 目录文件</button>
                </div>
              </div>
            </div>
          )}

          {activeView === 'curriculum' && (
            <div className="panel" style={{ maxWidth: '800px', margin: '0 auto' }}>
              <div className="panel-header"><h3>本科培养方案管理</h3></div>
              {curriculum ? (
                <div style={{ marginBottom: '32px', padding: '20px', background: 'var(--bg-workspace)', borderRadius: '12px', position: 'relative' }}>
                  <button 
                    className="btn btn-ghost" 
                    style={{ position: 'absolute', top: '12px', right: '12px', color: 'var(--danger)' }}
                    onClick={() => handleDeleteCurriculum(curriculum.id)}
                  >
                    🗑️ 删除此方案
                  </button>
                  <h4 style={{ margin: '0 0 12px 0' }}>当前生效方案：{curriculum.programName}</h4>
                  <p style={{ margin: '0 0 8px 0', fontSize: '14px' }}>文件：{curriculum.fileName} (v{curriculum.version})</p>
                  <p style={{ margin: 0, fontSize: '14px' }}>包含 <strong>{curriculum.requiredCourses}</strong> 门必修课，共 <strong>{curriculum.requiredModules}</strong> 个模块</p>
                </div>
              ) : (
                <p className="muted" style={{ textAlign: 'center', padding: '40px' }}>尚未配置培养方案，学生端学业分析将无法使用。</p>
              )}
              <div className="form-group">
                <label>更新培养方案 (支持 .json, .xlsx)</label>
                <label className="upload-area" style={{ display: 'block' }}>
                  <input type="file" onChange={handleCurriculumUpload} />
                  <div style={{ fontSize: '24px', marginBottom: '8px' }}>📜</div>
                  <strong>上传新方案文件</strong>
                  <p style={{ fontSize: '12px', color: 'var(--muted)' }}>系统将按最新上传的文件计算学生修读进度</p>
                </label>
              </div>
            </div>
          )}

          {activeView === 'users' && (
            <div className="panel">
              <div className="panel-header">
                <h3>学生档案中心</h3>
                <div style={{ display: 'flex', gap: '12px' }}>
                  <button className="btn btn-primary" onClick={() => setIsAddUserModalOpen(true)}>➕ 录入学生</button>
                  <button 
                    className="btn btn-ghost" 
                    onClick={() => adminApi.downloadFile(adminApi.getTemplateDownloadUrl('users'), 'users_import_template.xlsx')}
                    style={{ display: 'flex', alignItems: 'center' }}
                  >
                    📄 下载模板
                  </button>
                  <label className="btn btn-ghost">
                    📥 批量导入
                    <input type="file" style={{ display: 'none' }} onChange={handleUserImport} accept=".xlsx,.csv" />
                  </label>
                  <input 
                    className="form-control" 
                    placeholder="搜索姓名/学号/账号..." 
                    style={{ width: '240px' }}
                    value={userSearch}
                    onChange={e => setUserSearch(e.target.value)}
                  />
                  <select 
                    className="form-control" 
                    style={{ width: '120px' }}
                    value={userFilter.grade}
                    onChange={e => setUserFilter({ ...userFilter, grade: e.target.value })}
                  >
                    {gradeOptions.map(o => <option key={o} value={o}>{o}</option>)}
                  </select>
                  <select 
                    className="form-control" 
                    style={{ width: '150px' }}
                    value={userFilter.major}
                    onChange={e => setUserFilter({ ...userFilter, major: e.target.value })}
                  >
                    {majorOptions.map(o => <option key={o} value={o}>{o}</option>)}
                  </select>
                </div>
              </div>
              <div className="table-container">
                <table>
                  <thead>
                    <tr><th>学号</th><th>姓名</th><th>专业</th><th>年级</th><th>联系方式</th><th>操作</th></tr>
                  </thead>
                  <tbody>
                    {users.map(u => (
                      <tr key={u.id}>
                        <td>{u.studentNo || '-'}</td>
                        <td><strong>{u.realName}</strong></td>
                        <td>{u.major}</td>
                        <td>{u.grade}</td>
                        <td>
                          <div style={{ fontSize: '12px' }}>
                            <div>📧 {u.email || '未绑定'}</div>
                            <div>📱 {u.phone || '未绑定'}</div>
                          </div>
                        </td>
                        <td>
                          <div style={{ display: 'flex', gap: '8px' }}>
                            <button className="btn btn-ghost" style={{ padding: '4px 8px', fontSize: '12px', color: 'var(--primary)' }} onClick={() => handleViewUserDetail(u)}>详情</button>
                            <button className="btn btn-ghost" style={{ padding: '4px 8px', fontSize: '12px', color: 'var(--danger)' }} onClick={() => handleDeleteUser(u.id)}>注销</button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {users.length === 0 && (
                <div style={{ textAlign: 'center', padding: '40px', color: 'var(--muted)' }}>
                  未找到符合条件的档案
                </div>
              )}
            </div>
          )}
        </main>
      </div>

      {isAppModalOpen && selectedApp && (
        <div className="modal-overlay" onClick={() => setIsAppModalOpen(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h3>申请详情 - #{selectedApp.id}</h3>
              <button className="btn btn-ghost" onClick={() => setIsAppModalOpen(false)}>✕</button>
            </div>
            <div className="modal-body">
              <div className="panel" style={{ border: 'none', boxShadow: 'none', padding: 0, marginBottom: '24px' }}>
                <div className="detail-grid">
                  <div className="detail-item"><label>申请类型</label><span>{selectedApp.typeLabel}</span></div>
                  <div className="detail-item"><label>申请状态</label><StatusBadge status={selectedApp.status} /></div>
                  <div className="detail-item"><label>提交时间</label><span>{selectedApp.createdAt}</span></div>
                  <div className="detail-item"><label>最后更新</label><span>{selectedApp.updatedAt}</span></div>
                </div>
              </div>

              <div className="panel">
                <div className="panel-header"><h3>表单内容</h3></div>
                <div className="detail-grid">
                  {Object.entries(selectedApp.form || {}).map(([key, value]) => (
                    <div className="detail-item" key={key}>
                      <label>{key}</label>
                      <span>{String(value)}</span>
                    </div>
                  ))}
                </div>
              </div>

              {selectedApp.attachments && selectedApp.attachments.length > 0 && (
                <div className="panel" style={{ marginTop: '24px' }}>
                  <div className="panel-header"><h3>附件材料</h3></div>
                  <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
                    {selectedApp.attachments.map((file: any, index: number) => {
                      const fileName = file.name || file.fileName || '附件' + (index + 1);
                      return (
                        <div 
                          key={index} 
                          onClick={() => adminApi.downloadFile(file.url, fileName)}
                          style={{ 
                            padding: '8px 12px', 
                            background: 'var(--bg-workspace)', 
                            borderRadius: '8px', 
                            fontSize: '13px',
                            textDecoration: 'none',
                            color: 'inherit',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '6px',
                            cursor: 'pointer',
                            border: '1px solid var(--border)'
                          }}
                          className="attachment-link"
                        >
                          📎 {fileName}
                          <span style={{ fontSize: '10px', opacity: 0.5 }}>📥</span>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}

              <div className="timeline">
                {selectedApp.approvals?.map((node: any) => (
                  <div key={node.key} className={`timeline-item ${node.status}`}>
                    <div className="timeline-title">{node.title}</div>
                    {node.time && <div className="timeline-time">{node.time}</div>}
                    <div className="timeline-desc">{node.desc}</div>
                  </div>
                ))}
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-ghost" onClick={() => setIsAppModalOpen(false)}>关闭</button>
              {(selectedApp.status === '待审核' || selectedApp.status === '审批中') && (
                <>
                  <button className="btn btn-danger" onClick={() => handleAudit(selectedApp.id, 'reject')}>驳回申请</button>
                  <button className="btn btn-primary" onClick={() => handleAudit(selectedApp.id, 'pass')}>通过审批</button>
                </>
              )}
            </div>
          </div>
        </div>
      )}

      {isUserModalOpen && selectedUser && (
        <div className="modal-overlay" onClick={() => setIsUserModalOpen(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h3>学生档案详情 - {selectedUser.realName}</h3>
              <button className="btn btn-ghost" onClick={() => setIsUserModalOpen(false)}>✕</button>
            </div>
            <div className="modal-body">
              <div className="panel" style={{ border: 'none', boxShadow: 'none', padding: 0, marginBottom: '24px' }}>
                <div className="detail-grid">
                  <div className="detail-item"><label>姓名</label><span>{selectedUser.realName}</span></div>
                  <div className="detail-item"><label>学号</label><span>{selectedUser.studentNo || '-'}</span></div>
                  <div className="detail-item"><label>账号 (用户名)</label><span>{selectedUser.username}</span></div>
                  <div className="detail-item"><label>身份类型</label><span className="badge badge-info">{selectedUser.identity || '学生'}</span></div>
                  <div className="detail-item"><label>专业</label><span>{selectedUser.major}</span></div>
                  <div className="detail-item"><label>年级</label><span>{selectedUser.grade}</span></div>
                </div>
              </div>

              <div className="panel" style={{ marginBottom: '24px' }}>
                <div className="panel-header"><h3>隐私与联系信息</h3></div>
                <div className="detail-grid">
                  <div className="detail-item"><label>电子邮箱</label><span>{selectedUser.email || '未绑定'}</span></div>
                  <div className="detail-item"><label>手机号码</label><span>{selectedUser.phone || '未绑定'}</span></div>
                  <div className="detail-item"><label>身份证号</label><span>{selectedUser.idCard || '未登记'}</span></div>
                  <div className="detail-item"><label>账号状态</label><span className="badge badge-success">正常</span></div>
                </div>
              </div>

              <div className="panel">
                <div className="panel-header"><h3>快速操作</h3></div>
                <div style={{ display: 'flex', gap: '12px' }}>
                  <button className="btn btn-ghost" onClick={() => handleResetPassword(selectedUser.id)}>🔑 重置登录密码</button>
                  <button className="btn btn-ghost" onClick={() => {
                    setIsUserModalOpen(false)
                    setActiveView('applications')
                  }}>📂 查看此生申请记录</button>
                </div>
                <p style={{ fontSize: '12px', color: 'var(--muted)', marginTop: '12px' }}>
                  * 重置密码后，学生的登录密码将统一恢复为 <code>123456</code>，请提醒学生及时修改。
                </p>
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-primary" onClick={() => setIsUserModalOpen(false)}>完成</button>
            </div>
          </div>
        </div>
      )}

      {isAddUserModalOpen && (
        <div className="modal-overlay" onClick={() => setIsAddUserModalOpen(false)}>
          <div className="modal" style={{ maxWidth: '600px' }} onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h3>手动录入学生档案</h3>
              <button className="btn btn-ghost" onClick={() => setIsAddUserModalOpen(false)}>✕</button>
            </div>
            <form onSubmit={handleAddUser}>
              <div className="modal-body">
                <div className="grid" style={{ gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                  <div className="form-group">
                    <label>姓名 *</label>
                    <input className="form-control" required value={newUser.realName} onChange={e => setNewUser({...newUser, realName: e.target.value})} placeholder="学生姓名" />
                  </div>
                  <div className="form-group">
                    <label>学号 *</label>
                    <input className="form-control" required value={newUser.studentNo} onChange={e => setNewUser({...newUser, studentNo: e.target.value})} placeholder="学号 (将作为默认账号)" />
                  </div>
                  <div className="form-group">
                    <label>年级</label>
                    <select className="form-control" value={newUser.grade} onChange={e => setNewUser({...newUser, grade: e.target.value})}>
                      {gradeOptions.filter(o => o !== '全部').map(o => <option key={o} value={o}>{o}</option>)}
                    </select>
                  </div>
                  <div className="form-group">
                    <label>专业</label>
                    <select className="form-control" value={newUser.major} onChange={e => setNewUser({...newUser, major: e.target.value})}>
                      {majorOptions.filter(o => o !== '全部').map(o => <option key={o} value={o}>{o}</option>)}
                    </select>
                  </div>
                  <div className="form-group">
                    <label>身份类型</label>
                    <select className="form-control" value={newUser.identity} onChange={e => setNewUser({...newUser, identity: e.target.value})}>
                      {identityOptions.filter(o => o !== '全部').map(o => <option key={o} value={o}>{o}</option>)}
                    </select>
                  </div>
                  <div className="form-group">
                    <label>电子邮箱</label>
                    <input className="form-control" type="email" value={newUser.email} onChange={e => setNewUser({...newUser, email: e.target.value})} placeholder="可选" />
                  </div>
                </div>
                <div className="form-group">
                  <label>手机号码</label>
                  <input className="form-control" value={newUser.phone} onChange={e => setNewUser({...newUser, phone: e.target.value})} placeholder="可选" />
                </div>
                <div className="form-group">
                  <label>身份证号</label>
                  <input className="form-control" value={newUser.idCard} onChange={e => setNewUser({...newUser, idCard: e.target.value})} placeholder="可选" />
                </div>
                <p style={{ fontSize: '12px', color: 'var(--muted)' }}>* 录入后，默认登录密码为 <code>123456</code>。</p>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-ghost" onClick={() => setIsAddUserModalOpen(false)}>取消</button>
                <button type="submit" className="btn btn-primary">确认录入</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}

function NavItem({ active, onClick, icon, text }: { active: boolean, onClick: () => void, icon: string, text: string }) {
  return (
    <button className={`nav-item ${active ? 'active' : ''}`} onClick={onClick}>
      <span className="nav-icon">{icon}</span>
      <span className="nav-text">{text}</span>
    </button>
  )
}

function StatCard({ label, value, footer }: { label: string, value: string | number, footer: string }) {
  return (
    <div className="stat-card">
      <span className="stat-label">{label}</span>
      <span className="stat-value">{value}</span>
      <span className="stat-footer">{footer}</span>
    </div>
  )
}

function StatusBadge({ status }: { status: string | number }) {
  const s = String(status)
  if (s === '待审核' || s === '0') return <span className="badge badge-warning">待审核</span>
  if (s === '已通过' || s === '1') return <span className="badge badge-success">已通过</span>
  if (s === '已驳回' || s === '2') return <span className="badge badge-danger">已驳回</span>
  if (s === '已撤回' || s === '3') return <span className="badge badge-info">已撤回</span>
  return <span className="badge">未知</span>
}

function getViewTitle(view: ViewType) {
  switch (view) {
    case 'dashboard': return '工作台总览'
    case 'notifications': return '通知公告管理'
    case 'push': return '精准信息推送'
    case 'applications': return '事务审批中心'
    case 'users': return '学生档案管理'
    case 'knowledge': return 'AI 知识库维护'
    case 'curriculum': return '培养方案管理'
  }
}

export default App
