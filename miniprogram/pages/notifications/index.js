const notification = require("../../services/notification")
const { ensureLoggedIn } = require("../../utils/storage")

Page({
  data: {
    loading: false,
    errorMsg: "",
    list: [],
    expandedId: "",
    tagOptions: ["全部", "就业", "实习", "竞赛", "党团", "学业"],
    tagIndex: 0
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
      const tag = this.data.tagOptions[this.data.tagIndex] || "全部"
      const res = await notification.listNotifications({ tag })
      this.setData({ list: res.data || [] })
    } catch (e) {
      this.setData({ errorMsg: (e && e.message) || "加载失败" })
    } finally {
      this.setData({ loading: false })
    }
  },
  onFilterTap(e) {
    const idx = Number(e.currentTarget.dataset.index)
    if (!Number.isFinite(idx) || idx === this.data.tagIndex) return
    this.setData({ tagIndex: idx, expandedId: "" })
    this.load()
  },
  retry() {
    this.load()
  },
  async toggle(e) {
    const id = e.currentTarget.dataset.id
    if (!id) return
    try {
      if (this.data.expandedId === id) {
        this.setData({ expandedId: "" })
        return
      }

      const res = await notification.getNotificationDetail(id)
      const detail = res.data || {}
      const nextList = (this.data.list || []).map((x) => (x && x.id === id ? { ...x, ...detail } : x))
      this.setData({ list: nextList, expandedId: id })

      if (!detail.read) {
        await notification.markAsRead(id)
        const marked = (this.data.list || []).map((x) => (x && x.id === id ? { ...x, read: true } : x))
        this.setData({ list: marked })
      }
    } catch (e2) {
      wx.showToast({ title: (e2 && e2.message) || "获取失败", icon: "none" })
    }
  },
  copyLink(e) {
    const url = e.currentTarget.dataset.url
    if (!url) return
    wx.setClipboardData({ data: url })
  }
})
