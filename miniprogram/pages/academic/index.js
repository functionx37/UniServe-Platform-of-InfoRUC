const academic = require("../../services/academic")
const { ensureLoggedIn } = require("../../utils/storage")

Page({
  data: {
    loading: false,
    errorMsg: "",
    overview: null,
    completionPercent: 0,
    overviewItems: [],
    uploadSubtitle: "支持 PDF / Excel / CSV",
    riskSubtitle: "暂无风险提示"
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
      const res = await academic.getOverview()
      const overview = res.data || null
      const metricLabel = (overview && overview.metricLabel) || "学分"
      const total = overview ? Number(overview.totalCredits) || 0 : 0
      const earned = overview ? Number(overview.earnedCredits) || 0 : 0
      const completionPercent = total > 0 ? Math.round((earned / total) * 100) : 0
      const overviewItems = overview
        ? [
            { label: "培养方案总" + metricLabel, value: total + " " + metricLabel },
            { label: "已完成" + metricLabel, value: earned + " " + metricLabel },
            { label: "缺少" + metricLabel, value: (Number(overview.gapCredits) || 0) + " " + metricLabel }
          ]
        : []

      const uploadSubtitle =
        overview && overview.transcript && overview.transcript.fileName
          ? "最近上传：" +
            overview.transcript.fileName +
            (overview.transcript.parsed === false ? "（解析失败/未完成）" : "")
          : "支持 PDF / Excel / CSV"
      const riskCount = overview ? Number(overview.riskCount) || 0 : 0
      const riskSubtitle = riskCount > 0 ? "当前风险点：" + riskCount + " 项" : "暂无风险提示"

      this.setData({ overview, completionPercent, overviewItems, uploadSubtitle, riskSubtitle })
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
  },
  goResult() {
    wx.navigateTo({ url: "/pages/academic-result/index" })
  }
})
