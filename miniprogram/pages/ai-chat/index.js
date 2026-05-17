const ai = require("../../services/ai")
const { ensureLoggedIn } = require("../../utils/storage")

Page({
  data: {
    question: "",
    loading: false,
    errorMsg: "",
    lastQuestion: "",
    quickQuestions: [
      "请假流程怎么走？",
      "在读证明如何申请？",
      "党团流程当前阶段如何查看？",
      "成绩单上传后多久能看到分析结果？"
    ],
    messages: []
  },
  onLoad() {
    if (!ensureLoggedIn()) return
  },
  onShow() {
    if (!ensureLoggedIn()) return
  },
  onQuestion(e) {
    this.setData({ question: e.detail.value })
  },
  onQuickTap(e) {
    const q = e.currentTarget.dataset.q
    if (!q) return
    this.setData({ question: q })
    this.send()
  },
  onRelatedTap(e) {
    const q = e.currentTarget.dataset.q
    if (!q) return
    this.setData({ question: q })
    this.send()
  },
  copySource(e) {
    const url = e.currentTarget.dataset.url
    if (!url) return
    wx.setClipboardData({ data: url })
  },
  async retry() {
    if (!this.data.lastQuestion || this.data.loading) return
    this.setData({ question: this.data.lastQuestion })
    await this.send()
  },
  async send() {
    const q = String(this.data.question || "").trim()
    if (!q || this.data.loading) return

    const userMsg = {
      id: Date.now() + "-u",
      type: "user",
      text: q
    }
    this.setData({
      messages: this.data.messages.concat([userMsg]),
      loading: true,
      errorMsg: "",
      lastQuestion: q,
      question: ""
    })

    try {
      const res = await ai.askQuestion({ question: q })
      const data = (res && res.data) || {}
      const sysMsg = {
        id: Date.now() + "-s",
        type: "system",
        answer: data.answer || "",
        sourceTitle: data.sourceTitle || "",
        sourceUrl: data.sourceUrl || "",
        relatedQuestions: Array.isArray(data.relatedQuestions) ? data.relatedQuestions : []
      }
      this.setData({
        messages: this.data.messages.concat([sysMsg])
      })
    } catch (e) {
      this.setData({
        errorMsg: (e && e.message) || "请求失败"
      })
    } finally {
      this.setData({ loading: false })
    }
  }
})
