const application = require("../../services/application")
const fileService = require("../../services/file")
const { ensureLoggedIn } = require("../../utils/storage")
const { maskPhone } = require("../../utils/mask")

Page({
  data: {
    id: "",
    loading: false,
    errorMsg: "",
    detail: null,
    statusType: "info",
    formItems: [],
    attachments: [],
    approvals: [],
    approvalOpinions: [],
    result: null,
    metaItems: [],
    proofActions: null
  },
  onLoad(options) {
    this.setData({ id: options.id || "" })
    if (!ensureLoggedIn()) return
    this.loadDetail()
  },
  async loadDetail() {
    if (this.data.loading) return
    this.setData({ loading: true, errorMsg: "" })
    try {
      const res = await application.getApplicationDetail(this.data.id)
      const detail = res.data || null
      if (!detail) {
        this.setData({ detail: null })
        return
      }

      const formItems = this.buildFormItems(detail.typeKey, detail.form || {})
      const proofActions = this.buildProofActions(detail)
      this.setData({
        detail,
        statusType: this.getStatusType(detail.status),
        metaItems: [
          { label: "申请编号", value: detail.id || "—" },
          { label: "提交时间", value: detail.createdAt || "—" }
        ],
        formItems,
        attachments: Array.isArray(detail.attachments) ? detail.attachments : [],
        approvals: Array.isArray(detail.approvals) ? detail.approvals : [],
        approvalOpinions: Array.isArray(detail.approvalOpinions) ? detail.approvalOpinions : [],
        result: detail.result || null,
        proofActions
      })
    } catch (e) {
      this.setData({ errorMsg: (e && e.message) || "加载失败" })
    } finally {
      this.setData({ loading: false })
    }
  },
  retry() {
    this.loadDetail()
  },
  getStatusType(status) {
    const s = String(status || "")
    if (s === "已通过") return "success"
    if (s === "已驳回") return "danger"
    if (s === "审批中") return "warning"
    return "info"
  },
  buildFormItems(typeKey, form) {
    const f = form || {}
    if (typeKey === "leave") {
      return [
        { label: "请假开始", value: f.leaveStart || "—" },
        { label: "请假结束", value: f.leaveEnd || "—" },
        { label: "请假事由", value: f.reason || "—" },
        { label: "联系电话", value: f.contactPhone ? maskPhone(f.contactPhone) : "—" }
      ]
    }
    if (typeKey === "enrollment_cert") {
      return [
        { label: "用途说明", value: f.purpose || "—" },
        { label: "接收单位/部门", value: f.receiver || "—" }
      ]
    }
    if (typeKey === "political_cert") {
      return [
        { label: "用途说明", value: f.purpose || "—" },
        { label: "接收单位/部门", value: f.receiver || "—" }
      ]
    }
    const pairs = Object.keys(f).map((k) => ({ label: k, value: String(f[k]) }))
    return pairs.length ? pairs : [{ label: "表单内容", value: "—" }]
  },
  buildProofActions(detail) {
    const d = detail || {}
    if (d && d.result && d.result.url) {
      return {
        title: (d.result && d.result.title) || "结果文件",
        url: d.result.url
      }
    }
    if (String(d.status || "") === "已通过" && String(d.typeKey || "") === "leave") {
      const f = d.form || {}
      const params = []
      params.push(`typeKey=${encodeURIComponent("leave")}`)
      params.push(`leaveStart=${encodeURIComponent(String(f.leaveStart || ""))}`)
      params.push(`leaveEnd=${encodeURIComponent(String(f.leaveEnd || ""))}`)
      params.push(`reason=${encodeURIComponent(String(f.reason || ""))}`)
      return {
        title: "请假证明（PDF）",
        url: `/files/proofs/preview?${params.join("&")}`
      }
    }
    return null
  },
  copyUrl(e) {
    const url = e.currentTarget.dataset.url
    if (!url) return
    wx.setClipboardData({ data: url })
  },
  previewProof() {
    if (!this.data.proofActions || !this.data.proofActions.url) return
    wx.showLoading({ title: "打开中...", mask: true })
    fileService
      .downloadAndOpenDocument({ url: this.data.proofActions.url, fileType: "pdf", showMenu: false })
      .catch((e) => {
        wx.showModal({
          title: (this.data.proofActions && this.data.proofActions.title) || "申请结果",
          content: `无法直接预览（${(e && e.message) || "未知错误"}），请复制链接后在浏览器打开下载。`,
          confirmText: "复制链接",
          success: (r) => {
            if (r.confirm) wx.setClipboardData({ data: this.data.proofActions.url })
          }
        })
      })
      .finally(() => {
        wx.hideLoading()
      })
  },
  downloadProof() {
    if (!this.data.proofActions || !this.data.proofActions.url) return
    wx.showLoading({ title: "下载中...", mask: true })
    fileService
      .downloadAndOpenDocument({ url: this.data.proofActions.url, fileType: "pdf", showMenu: true })
      .catch((e) => {
        wx.showModal({
          title: (this.data.proofActions && this.data.proofActions.title) || "申请结果",
          content: `无法直接下载（${(e && e.message) || "未知错误"}），请复制链接后在浏览器打开下载。`,
          confirmText: "复制链接",
          success: (r) => {
            if (r.confirm) wx.setClipboardData({ data: this.data.proofActions.url })
          }
        })
      })
      .finally(() => {
        wx.hideLoading()
      })
  }
})
