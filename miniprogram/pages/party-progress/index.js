const party = require("../../services/party")
const { ensureLoggedIn } = require("../../utils/storage")

Page({
  data: {
    loading: false,
    errorMsg: "",
    progress: null,
    stageItems: []
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
      const res = await party.getPartyProgress()
      const progress = res.data || null
      const percent = progress ? Number(progress.progressPercent) || 0 : 0
      this.setData({
        progress,
        stageItems: [{ label: "整体进度", value: percent + "%" }]
      })
    } catch (e) {
      this.setData({ errorMsg: (e && e.message) || "加载失败" })
    } finally {
      this.setData({ loading: false })
    }
  },
  retry() {
    this.load()
  }
})
