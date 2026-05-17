function ok(data, message) {
  return {
    success: true,
    message: message || "操作成功",
    data: data
  }
}

function fail(message) {
  return {
    success: false,
    message: message || "操作失败",
    data: null
  }
}

const db = {
  token: "mock-jwt-token",
  user: {
    id: "u-001",
    roleId: 4,
    displayName: "同学C",
    studentNo: "20260001",
    grade: "2026",
    major: "信息学院",
    phone: "13800000000",
    idCard: "110101199001010000"
  },
  applications: [
    {
      id: "app-001",
      typeKey: "leave",
      typeLabel: "请假申请",
      title: "请假申请（事由：参加竞赛）",
      status: "审批中",
      createdAt: "2026-05-10 10:00",
      form: {
        leaveStart: "2026-05-12",
        leaveEnd: "2026-05-14",
        reason: "参加校级学科竞赛集训",
        contactPhone: "13800000000"
      },
      attachments: [{ name: "竞赛通知.pdf", url: "https://example.com/files/notice.pdf" }],
      approvals: [
        { key: "s1", title: "提交申请", desc: "申请已提交", time: "2026-05-10 10:00", status: "done", opinion: "" },
        { key: "s2", title: "辅导员审核", desc: "审核中", time: "", status: "current", opinion: "" },
        { key: "s3", title: "学院审批", desc: "待处理", time: "", status: "todo", opinion: "" },
        { key: "s4", title: "结果反馈", desc: "待完成", time: "", status: "todo", opinion: "" }
      ]
    },
    {
      id: "app-002",
      typeKey: "enrollment_cert",
      typeLabel: "在读证明",
      title: "在读证明申请（用途：奖助评审）",
      status: "已通过",
      createdAt: "2026-05-08 14:30",
      form: {
        purpose: "奖助评审材料提交",
        receiver: "信息学院学生工作办公室"
      },
      attachments: [],
      approvals: [
        { key: "s1", title: "提交申请", desc: "申请已提交", time: "2026-05-08 14:30", status: "done", opinion: "" },
        { key: "s2", title: "材料审核", desc: "审核通过", time: "2026-05-08 16:10", status: "done", opinion: "材料齐全，准予开具。" },
        { key: "s3", title: "生成证明", desc: "已生成电子证明", time: "2026-05-08 16:20", status: "done", opinion: "" }
      ],
      result: {
        downloadable: true,
        title: "在读证明.pdf",
        url: "https://example.com/files/enrollment.pdf"
      }
    },
    {
      id: "app-003",
      typeKey: "political_cert",
      typeLabel: "政治面貌证明",
      title: "政治面貌证明申请（用途：政审材料）",
      status: "已驳回",
      createdAt: "2026-05-06 09:10",
      form: {
        purpose: "政审材料提交",
        receiver: "用人单位人事部门"
      },
      attachments: [{ name: "政审通知截图.png", url: "https://example.com/files/check.png" }],
      approvals: [
        { key: "s1", title: "提交申请", desc: "申请已提交", time: "2026-05-06 09:10", status: "done", opinion: "" },
        {
          key: "s2",
          title: "材料审核",
          desc: "已驳回",
          time: "2026-05-06 10:05",
          status: "done",
          opinion: "用途说明不完整，请补充具体单位名称与用途场景后重新提交。"
        },
        { key: "s3", title: "结果反馈", desc: "已结束", time: "2026-05-06 10:05", status: "done", opinion: "" }
      ]
    }
  ],
  notifications: [
    {
      id: "notice-001",
      title: "信息学院实习报备与安全管理通知",
      tag: "实习",
      publishAt: "2026-05-02 09:00",
      read: false,
      content: "请严格按照学院发布的报备流程完成实习报备，并在规定时间内提交材料。具体要求以官方文件为准。",
      links: [{ title: "实习报备官方指南", url: "https://example.com/policy/internship" }]
    },
    {
      id: "notice-002",
      title: "就业三方协议办理说明（更新）",
      tag: "就业",
      publishAt: "2026-05-05 10:30",
      read: true,
      content: "三方协议办理以学校就业指导中心要求为准。请先完成信息填报，再按学院通知提交材料。",
      links: [{ title: "就业中心官方入口", url: "https://example.com/policy/career" }]
    },
    {
      id: "notice-003",
      title: "党团组织生活与材料提交提醒",
      tag: "党团",
      publishAt: "2026-05-08 18:00",
      read: false,
      content: "本月组织生活材料请按要求提交。系统仅提供提醒与入口，材料规范以学院党团文件为准。",
      links: [{ title: "党团材料规范（官方）", url: "https://example.com/policy/party-material" }]
    },
    {
      id: "notice-004",
      title: "学业预警说明与辅导安排",
      tag: "学业",
      publishAt: "2026-05-10 09:15",
      read: false,
      content: "如存在学分缺口或必修未完成，请及时联系导师/辅导员并制定补修计划。具体规则以培养方案为准。",
      links: [{ title: "培养方案与学业规则（官方）", url: "https://example.com/policy/academic" }]
    },
    {
      id: "notice-005",
      title: "学院竞赛报名与指导安排",
      tag: "竞赛",
      publishAt: "2026-05-12 14:00",
      read: true,
      content: "竞赛报名请按通知要求提交报名表与材料。指导安排与答疑时间以后续通知为准。",
      links: [{ title: "竞赛通知（官方）", url: "https://example.com/policy/contest" }]
    }
  ],
  templates: [
    {
      id: "tpl-001",
      title: "在读证明申请模板",
      scene: "在读证明",
      fileType: "pdf",
      updatedAt: "2026-05-01 12:00",
      url: "https://example.com/template"
    },
    {
      id: "tpl-002",
      title: "请假申请表",
      scene: "请假申请",
      fileType: "docx",
      updatedAt: "2026-05-03 11:20",
      url: "https://example.com/template/leave"
    },
    {
      id: "tpl-003",
      title: "思想汇报模板",
      scene: "党团材料",
      fileType: "docx",
      updatedAt: "2026-05-06 09:00",
      url: "https://example.com/template/party"
    }
  ]
}

const academicState = {
  transcript: {
    fileId: "file-001",
    fileName: "成绩单示例.pdf",
    uploadedAt: "2026-05-15 16:20",
    parsed: true
  }
}

function mockRequest(options) {
  const url = options.url || ""
  const method = (options.method || "GET").toUpperCase()
  const data = options.data || {}

  if (url === "/auth/login" && method === "POST") {
    const studentNo = String(data.studentNo || data.username || "").trim()
    const name = String(data.name || "").trim()
    const password = String(data.password || "").trim()

    if (!studentNo) return fail("请输入学号")
    if (!name && !password) return fail("请输入姓名")

    db.user = {
      ...db.user,
      studentNo: studentNo,
      displayName: name || db.user.displayName
    }

    return ok(
      {
        token: db.token,
        role: "学生",
        displayName: db.user.displayName,
        userInfo: db.user
      },
      "登录成功"
    )
  }

  if (url === "/user/me" && method === "GET") {
    return ok(db.user)
  }

  if (url === "/ai/ask" && method === "POST") {
    const q = String(data.question || "").trim()
    if (!q) return fail("问题不能为空")
    const normalized = q.replace(/\s+/g, "")
    if (normalized.indexOf("请假") >= 0) {
      return ok({
        answer:
          "关于请假流程，请以学院发布的《学生请假管理办法》为准。通常包括：提交申请、辅导员审核、学院审批与结果反馈。具体材料要求与时限以文件条款为准。",
        sourceTitle: "《学生请假管理办法》（学院官方）",
        sourceUrl: "https://example.com/policy/leave",
        relatedQuestions: ["请假需要哪些证明材料？", "请假审批一般需要多久？", "如何查看请假申请状态？"]
      })
    }
    if (normalized.indexOf("证明") >= 0) {
      return ok({
        answer:
          "电子证明（如在读证明）以系统自动生成的版本为准。若需线下盖章或补充材料，请以学院通知要求为准。",
        sourceTitle: "《电子证明开具说明》（学院官方）",
        sourceUrl: "https://example.com/policy/certificate",
        relatedQuestions: ["在读证明如何下载？", "电子证明是否需要盖章？", "申请被驳回如何补充材料？"]
      })
    }
    if (normalized.indexOf("党团") >= 0 || normalized.indexOf("入党") >= 0) {
      return ok({
        answer:
          "党团流程以学院党委/团委发布的标准路径为准。本系统仅展示标准路径、当前阶段与待办任务；如需调整节点或材料要求，请以官方文件与组织通知为准。",
        sourceTitle: "《党团流程标准路径与材料清单》（学院官方）",
        sourceUrl: "https://example.com/policy/party",
        relatedQuestions: ["当前阶段需要完成哪些任务？", "材料提交截止时间如何查看？", "流程节点由谁审核？"]
      })
    }
    return ok({
      answer:
        "已为你匹配到与该问题相关的官方政策入口。为确保准确性，建议优先查阅以下官方文件链接；若仍有疑问，可在问题中补充具体场景（如年级、事项类型）。",
      sourceTitle: "学院政策与办事指南（官方汇总）",
      sourceUrl: "https://example.com/policy",
      relatedQuestions: ["如何提交事务申请？", "如何查看通知公告？", "如何上传成绩单进行学业分析？"]
    })
  }

  if (url === "/party/progress" && method === "GET") {
    return ok({
      currentStage: "积极分子",
      progressPercent: 35,
      nodes: [
        {
          key: "n1",
          title: "提交入党申请书",
          desc: "提交申请书并完成信息登记",
          time: "2026-03-12",
          status: "done"
        },
        {
          key: "n2",
          title: "确定入党积极分子",
          desc: "组织考察与材料审核",
          time: "2026-04-08",
          status: "current"
        },
        {
          key: "n3",
          title: "参加党课培训",
          desc: "按要求完成党课学习与测评",
          time: "",
          status: "todo"
        },
        {
          key: "n4",
          title: "确定发展对象",
          desc: "公示与组织审查",
          time: "",
          status: "todo"
        },
        {
          key: "n5",
          title: "接收为预备党员",
          desc: "支部大会讨论与上级审批",
          time: "",
          status: "todo"
        }
      ],
      todos: [
        {
          title: "提交思想汇报",
          dueAt: "2026-05-28 18:00",
          note: "请按模板提交，确保落款日期与文件名规范"
        },
        {
          title: "完成党课学习记录",
          dueAt: "2026-06-05 18:00",
          note: "学习记录需包含课程名称与学习时长"
        }
      ]
    })
  }

  if (url === "/applications" && method === "GET") {
    const status = String(data.status || "全部")
    const list = db.applications.slice()
    const filtered = status === "全部" ? list : list.filter((x) => x && x.status === status)
    const summary = filtered.map((x) => ({
      id: x.id,
      typeKey: x.typeKey,
      typeLabel: x.typeLabel,
      title: x.title,
      status: x.status,
      createdAt: x.createdAt
    }))
    return ok(summary)
  }

  if (url === "/applications" && method === "POST") {
    const typeKey = String(data.typeKey || "").trim()
    const form = data.form || {}
    const attachments = Array.isArray(data.attachments) ? data.attachments : []
    if (!typeKey) return fail("请选择申请类型")

    const typeLabelMap = {
      leave: "请假申请",
      enrollment_cert: "在读证明",
      political_cert: "政治面貌证明"
    }
    const typeLabel = typeLabelMap[typeKey] || "事务申请"

    if (typeKey === "leave") {
      if (!String(form.leaveStart || "").trim()) return fail("请选择请假开始日期")
      if (!String(form.leaveEnd || "").trim()) return fail("请选择请假结束日期")
      if (!String(form.reason || "").trim()) return fail("请填写请假事由")
      if (!String(form.contactPhone || "").trim()) return fail("请填写联系电话")
    }
    if (typeKey === "enrollment_cert") {
      if (!String(form.purpose || "").trim()) return fail("请填写用途说明")
      if (!String(form.receiver || "").trim()) return fail("请填写接收单位/部门")
    }
    if (typeKey === "political_cert") {
      if (!String(form.purpose || "").trim()) return fail("请填写用途说明")
      if (!String(form.receiver || "").trim()) return fail("请填写接收单位/部门")
    }

    const id = "app-" + String(Date.now())
    const item = {
      id,
      typeKey,
      typeLabel,
      title: typeLabel + "申请",
      status: "审批中",
      createdAt: "2026-05-16 12:00",
      form,
      attachments,
      approvals: [
        { key: "s1", title: "提交申请", desc: "申请已提交", time: "2026-05-16 12:00", status: "done", opinion: "" },
        { key: "s2", title: "材料审核", desc: "审核中", time: "", status: "current", opinion: "" },
        { key: "s3", title: "学院审批", desc: "待处理", time: "", status: "todo", opinion: "" },
        { key: "s4", title: "结果反馈", desc: "待完成", time: "", status: "todo", opinion: "" }
      ]
    }
    db.applications.unshift(item)
    return ok({ id: item.id }, "提交成功")
  }

  if (url.startsWith("/applications/") && method === "GET") {
    const id = url.replace("/applications/", "")
    const item = db.applications.find((x) => x.id === id)
    if (!item) return fail("未找到申请")
    const approvalOpinions = (item.approvals || [])
      .filter((x) => x && x.opinion)
      .map((x) => ({ step: x.title, time: x.time, opinion: x.opinion }))

    return ok({
      id: item.id,
      typeKey: item.typeKey,
      typeLabel: item.typeLabel,
      title: item.title,
      status: item.status,
      createdAt: item.createdAt,
      form: item.form || {},
      attachments: item.attachments || [],
      approvals: item.approvals || [],
      approvalOpinions,
      result: item.result || null
    })
  }

  if (url === "/academic/status" && method === "GET") {
    const totalCredits = 100
    const earnedCredits = 85
    const gapCredits = Math.max(0, totalCredits - earnedCredits)
    const riskCount = gapCredits > 0 ? 2 : 0
    return ok({
      transcript: academicState.transcript,
      totalCredits,
      earnedCredits,
      gapCredits,
      riskCount
    })
  }

  if (url === "/academic/analysis" && method === "GET") {
    if (!academicState.transcript || !academicState.transcript.parsed) {
      return fail("尚未上传或解析成绩单，请先上传成绩单")
    }

    const totalCredits = 100
    const earnedCredits = 85
    const gapCredits = Math.max(0, totalCredits - earnedCredits)
    const modules = [
      { key: "general", title: "通识课程", requiredCredits: 20, earnedCredits: 16 },
      { key: "major_required", title: "专业必修", requiredCredits: 40, earnedCredits: 32 },
      { key: "major_elective", title: "专业选修", requiredCredits: 30, earnedCredits: 24 },
      { key: "practice", title: "实践环节", requiredCredits: 10, earnedCredits: 13 }
    ].map((m) => {
      const required = Number(m.requiredCredits) || 0
      const earned = Number(m.earnedCredits) || 0
      const percent = required <= 0 ? 100 : Math.min(100, Math.round((earned / required) * 100))
      const gap = Math.max(0, required - earned)
      return { ...m, percent, gapCredits: gap }
    })

    const missingRequiredCourses = [
      { course: "数据结构", reason: "专业必修未完成" },
      { course: "操作系统", reason: "专业必修未完成" }
    ]
    const risks = [
      `总学分缺口 ${gapCredits} 学分`,
      `专业必修未完成：${missingRequiredCourses.map((x) => x.course).join("、")}`
    ]
    const suggestions = [
      "优先补齐“专业必修”缺口课程，并在下学期完成对应实验或实践环节。",
      "在满足必修后，按培养方案选择“专业选修”补足学分，避免集中在毕业前补修。"
    ]

    return ok({
      transcript: academicState.transcript,
      totalCredits,
      earnedCredits,
      gapCredits,
      modules,
      missingRequiredCourses,
      risks,
      suggestions
    })
  }

  if (url === "/notifications" && method === "GET") {
    const tag = String(data.tag || "全部")
    const list = db.notifications.slice()
    const filtered = tag === "全部" ? list : list.filter((x) => x && x.tag === tag)
    const summary = filtered.map(({ content, ...rest }) => rest)
    return ok(summary)
  }

  if (url.startsWith("/notifications/") && method === "GET") {
    const id = url.replace("/notifications/", "")
    const item = db.notifications.find((x) => x.id === id)
    if (!item) return fail("未找到通知")
    return ok(item)
  }

  if (url === "/notifications/read" && method === "POST") {
    const id = String(data.id || "")
    const item = db.notifications.find((x) => x.id === id)
    if (!item) return fail("未找到通知")
    item.read = true
    return ok({ id }, "已标记为已读")
  }

  if (url === "/templates" && method === "GET") {
    return ok(db.templates)
  }

  if (url === "/templates/preview" && method === "POST") {
    const id = String(data.id || "")
    const item = db.templates.find((x) => x.id === id)
    if (!item) return fail("未找到模板")
    return ok({ id: item.id, url: item.url, fileType: item.fileType, title: item.title }, "预览链接已生成")
  }

  if (url === "/templates/download" && method === "POST") {
    const id = String(data.id || "")
    const item = db.templates.find((x) => x.id === id)
    if (!item) return fail("未找到模板")
    return ok({ id: item.id, url: item.url, fileType: item.fileType, title: item.title }, "下载链接已生成")
  }

  if (url.startsWith("/templates/") && method === "GET") {
    const id = url.replace("/templates/", "")
    const item = db.templates.find((x) => x.id === id)
    if (!item) return fail("未找到模板")
    return ok(item)
  }

  return fail("未实现的 Mock 接口：" + method + " " + url)
}

function mockUploadFile(options) {
  const url = options && options.url ? String(options.url) : ""
  if (url === "/academic/transcript/upload") {
    const fileName = String((options && options.fileName) || "成绩单.pdf")
    const lower = fileName.toLowerCase()
    if (lower.indexOf("invalid") >= 0 || lower.indexOf("错误") >= 0) {
      return fail("解析失败：文件内容无法识别，请确认上传的是规范成绩单文件")
    }
    const fileId = "file-" + String(Date.now())
    academicState.transcript = {
      fileId,
      fileName,
      uploadedAt: "2026-05-16 12:00",
      parsed: true
    }
    return ok({ fileId }, "上传并解析成功")
  }
  return ok({ fileId: "file-001" }, "上传成功")
}

module.exports = {
  mockRequest,
  mockUploadFile
}
