const template = require("../../services/template")
const { ensureLoggedIn } = require("../../utils/storage")

Page({
  data: {
    loading: false,
    errorMsg: "",
    list: []
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
      const res = await template.listTemplates()
      this.setData({ list: res.data || [] })
    } catch (e) {
      this.setData({ errorMsg: (e && e.message) || "加载失败" })
    } finally {
      this.setData({ loading: false })
    }
  },
  retry() {
    this.load()
  },
  async preview(e) {
    const id = e.currentTarget.dataset.id
    if (!id) return
    try {
      const res = await template.previewTemplate(id)
      const url = res && res.data && res.data.url
      wx.showModal({
        title: "预览",
        content: url ? "已生成预览链接，可复制后打开。" : "预览链接生成失败",
        confirmText: "复制链接",
        showCancel: false,
        success: () => {
          if (url) wx.setClipboardData({ data: url })
        }
      })
    } catch (e2) {
      wx.showToast({ title: (e2 && e2.message) || "预览失败", icon: "none" })
    }
  },
  async download(e) {
    const id = e.currentTarget.dataset.id
    if (!id) return
    try {
      const res = await template.downloadTemplate(id)
      const url = res && res.data && res.data.url
      if (!url) {
        wx.showToast({ title: "下载链接生成失败", icon: "none" })
        return
      }
      wx.setClipboardData({ data: url })
      wx.showToast({ title: "已复制下载链接", icon: "success" })
    } catch (e2) {
      wx.showToast({ title: (e2 && e2.message) || "下载失败", icon: "none" })
    }
  }
})
