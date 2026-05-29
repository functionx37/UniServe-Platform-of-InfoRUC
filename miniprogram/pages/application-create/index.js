const application = require("../../services/application")
const { required } = require("../../utils/validator")
const { ensureLoggedIn } = require("../../utils/storage")

Page({
  data: {
    types: [
      { key: "leave", label: "请假申请" },
      { key: "enrollment_cert", label: "在读证明" },
      { key: "political_cert", label: "政治面貌证明" }
    ],
    typeIndex: 0,
    loading: false,
    errorMsg: "",
    form: {
      leaveStart: "",
      leaveEnd: "",
      reason: "",
      contactPhone: "",
      purpose: "",
      receiver: ""
    },
    attachment: {
      name: "",
      path: ""
    }
  },
  onLoad() {
    if (!ensureLoggedIn()) return
  },
  onShow() {
    if (!ensureLoggedIn()) return
  },
  onType(e) {
    this.setData({ typeIndex: Number(e.detail.value || 0) })
  },
  onField(e) {
    const key = e.currentTarget.dataset.key
    const value = e.detail.value
    if (!key) return
    this.setData({ form: { ...this.data.form, [key]: value } })
  },
  onDateChange(e) {
    const key = e.currentTarget.dataset.key
    const value = e.detail.value
    if (!key) return
    const nextForm = { ...this.data.form, [key]: value }
    this.setData({ form: nextForm })

    const selected = this.data.types[this.data.typeIndex] || {}
    if (selected.key !== "leave") return

    const start = String(nextForm.leaveStart || "")
    const end = String(nextForm.leaveEnd || "")
    if (!start || !end) return

    if (end < start) {
      wx.showModal({
        title: "日期不合法",
        content: "请假结束日期不能早于请假开始日期，请重新选择。",
        showCancel: false
      })
      const fixedForm = { ...nextForm }
      if (key === "leaveEnd") fixedForm.leaveEnd = ""
      if (key === "leaveStart") fixedForm.leaveStart = ""
      this.setData({ form: fixedForm })
    }
  },
  onAttachmentChange(e) {
    const d = e.detail || {}
    this.setData({ attachment: { name: d.name || "", path: d.path || "" } })
  },
  onAttachmentRemove() {
    this.setData({ attachment: { name: "", path: "" } })
  },
  validate(typeKey, form) {
    const f = form || {}
    if (typeKey === "leave") {
      if (!required(f.leaveStart)) return "请选择请假开始日期"
      if (!required(f.leaveEnd)) return "请选择请假结束日期"
      if (String(f.leaveEnd) < String(f.leaveStart)) return "请假结束日期不能早于请假开始日期"
      if (!required(f.reason)) return "请填写请假事由"
      if (!required(f.contactPhone)) return "请填写联系电话"
    }
    if (typeKey === "enrollment_cert") {
      if (!required(f.purpose)) return "请填写用途说明"
      if (!required(f.receiver)) return "请填写接收单位/部门"
    }
    if (typeKey === "political_cert") {
      if (!required(f.purpose)) return "请填写用途说明"
      if (!required(f.receiver)) return "请填写接收单位/部门"
    }
    return ""
  },
  async submit() {
    if (this.data.loading) return
    this.setData({ loading: true, errorMsg: "" })
    try {
      const selected = this.data.types[this.data.typeIndex] || {}
      const typeKey = selected.key
      const form = this.data.form || {}
      const err = this.validate(typeKey, form)
      if (err) {
        if (typeKey === "leave" && err.indexOf("结束日期不能早于开始日期") >= 0) {
          wx.showModal({
            title: "日期不合法",
            content: "请假结束日期不能早于请假开始日期，请重新选择。",
            showCancel: false
          })
          return
        }
        this.setData({ errorMsg: err })
        return
      }

      const attachments = this.data.attachment.name
        ? [{ name: this.data.attachment.name, url: "" }]
        : []

      const res = await application.createApplication({ typeKey, form, attachments })
      const id = res && res.data && res.data.id
      wx.showModal({
        title: "提交成功",
        content: `申请编号：${id}`,
        showCancel: false,
        success: () => {
          wx.redirectTo({ url: `/pages/application-detail/index?id=${id}` })
        }
      })
    } catch (e) {
      this.setData({ errorMsg: (e && e.message) || "提交失败" })
    } finally {
      this.setData({ loading: false })
    }
  }
})
