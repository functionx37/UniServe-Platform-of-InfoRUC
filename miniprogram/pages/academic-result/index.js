const academic = require("../../services/academic")
const { ensureLoggedIn } = require("../../utils/storage")

Page({
  data: {
    loading: false,
    errorMsg: "",
    analysis: null,
    summaryItems: []
  },
  onLoad() {
    if (!ensureLoggedIn()) return
  },
  onShow() {
    if (!ensureLoggedIn()) return
    this.load()
  },
  async load() {
    if (this.data.loading) return
    this.setData({ loading: true, errorMsg: "" })
    try {
      const res = await academic.getAnalysis()
      const analysis = res.data || null
      const summaryItems = analysis
        ? [
            { label: "总学分", value: (Number(analysis.totalCredits) || 0) + " 学分" },
            { label: "已完成", value: (Number(analysis.earnedCredits) || 0) + " 学分" },
            { label: "缺少", value: (Number(analysis.gapCredits) || 0) + " 学分" }
          ]
        : []
      this.setData({ analysis, summaryItems })
    } catch (e) {
      this.setData({ errorMsg: (e && e.message) || "加载失败" })
    } finally {
      this.setData({ loading: false })
    }
  },
  retry() {
    this.load()
  },
  goUpload() {
    wx.navigateTo({ url: "/pages/transcript-upload/index" })
  }
})
