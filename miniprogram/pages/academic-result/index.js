const academic = require("../../services/academic")
const { ensureLoggedIn } = require("../../utils/storage")

Page({
  data: {
    loading: false,
    errorMsg: "",
    analysis: null,
    metricLabel: "学分",
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
      const metricLabel = (analysis && analysis.metricLabel) || "学分"
      const summaryItems = analysis
        ? [
            { label: "总" + metricLabel, value: (Number(analysis.totalCredits) || 0) + " " + metricLabel },
            { label: "已完成", value: (Number(analysis.earnedCredits) || 0) + " " + metricLabel },
            { label: "缺少", value: (Number(analysis.gapCredits) || 0) + " " + metricLabel }
          ]
        : []
      this.setData({ analysis, metricLabel, summaryItems })
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
