const party = require("../../services/party")
const { ensureLoggedIn } = require("../../utils/storage")

Page({
  data: {
    loading: false,
    errorMsg: "",
    progress: null,
    stageItems: [],
    overrideIndex: null
  },
  onLoad() {
    if (!ensureLoggedIn()) return
  },
  onShow() {
    if (!ensureLoggedIn()) return
    this.load()
  },
  getSavedOverrideIndex() {
    try {
      const v = wx.getStorageSync("party_progress_override_index")
      const n = Number(v)
      return Number.isFinite(n) ? n : null
    } catch (e) {
      return null
    }
  },
  saveOverrideIndex(index) {
    try {
      if (index === null || index === undefined) {
        wx.removeStorageSync("party_progress_override_index")
      } else {
        wx.setStorageSync("party_progress_override_index", Number(index))
      }
    } catch (e) {}
  },
  findCurrentIndex(nodes) {
    const list = Array.isArray(nodes) ? nodes : []
    const idx = list.findIndex((n) => n && n.status === "current")
    return idx >= 0 ? idx : 0
  },
  buildDisplayProgress(progress, overrideIndex) {
    if (!progress || !Array.isArray(progress.nodes) || !progress.nodes.length) return progress
    const nodes = progress.nodes.map((n) => ({ ...n }))
    const safeIndex = Math.max(0, Math.min(nodes.length - 1, Number(overrideIndex)))
    for (let i = 0; i < nodes.length; i++) {
      nodes[i].status = i < safeIndex ? "done" : i === safeIndex ? "current" : "todo"
    }
    const doneCount = safeIndex
    const percent = nodes.length > 0 ? Math.floor((doneCount * 100) / nodes.length) : 0
    const currentTitle = nodes[safeIndex] && nodes[safeIndex].title ? nodes[safeIndex].title : "—"
    const todos = [
      {
        title: "完成阶段：" + currentTitle,
        dueAt: "",
        note: nodes[safeIndex] && nodes[safeIndex].desc ? nodes[safeIndex].desc : ""
      }
    ]
    return {
      ...progress,
      currentStage: currentTitle,
      progressPercent: percent,
      nodes,
      todos
    }
  },
  async load() {
    if (this.data.loading) return
    this.setData({ loading: true, errorMsg: "" })
    try {
      const res = await party.getPartyProgress()
      const rawProgress = res.data || null
      const saved = this.getSavedOverrideIndex()
      const overrideIndex =
        saved === null || saved === undefined
          ? rawProgress && rawProgress.nodes
            ? this.findCurrentIndex(rawProgress.nodes)
            : null
          : saved
      const progress = overrideIndex === null ? rawProgress : this.buildDisplayProgress(rawProgress, overrideIndex)
      const percent = progress ? Number(progress.progressPercent) || 0 : 0
      this.setData({
        progress,
        stageItems: [{ label: "整体进度", value: percent + "%" }],
        overrideIndex
      })
    } catch (e) {
      this.setData({ errorMsg: (e && e.message) || "加载失败" })
    } finally {
      this.setData({ loading: false })
    }
  },
  retry() {
    this.load()
  },
  adjustStage(delta) {
    const p = this.data.progress
    if (!p || !Array.isArray(p.nodes) || !p.nodes.length) return
    const currentIndex = this.findCurrentIndex(p.nodes)
    const nextIndex = Math.max(0, Math.min(p.nodes.length - 1, currentIndex + delta))
    this.saveOverrideIndex(nextIndex)
    const progress = this.buildDisplayProgress(p, nextIndex)
    const percent = progress ? Number(progress.progressPercent) || 0 : 0
    this.setData({
      progress,
      stageItems: [{ label: "整体进度", value: percent + "%" }],
      overrideIndex: nextIndex
    })
  },
  prevStage() {
    this.adjustStage(-1)
  },
  nextStage() {
    this.adjustStage(1)
  },
  resetStage() {
    this.saveOverrideIndex(null)
    this.load()
  }
})
