const application = require("../../services/application")
const { ensureLoggedIn } = require("../../utils/storage")

Page({
  data: {
    loading: false,
    errorMsg: "",
    list: [],
    statusOptions: ["全部", "审批中", "已通过", "已驳回"],
    statusIndex: 0
  },
  onLoad() {
    if (!ensureLoggedIn()) return
  },
  onShow() {
    if (!ensureLoggedIn()) return
    this.loadList()
  },
  async loadList() {
    if (this.data.loading) return
    this.setData({ loading: true, errorMsg: "" })
    try {
      const status = this.data.statusOptions[this.data.statusIndex] || "全部"
      const res = await application.listApplications({ status })
      this.setData({ list: res.data || [] })
    } catch (e) {
      this.setData({ errorMsg: (e && e.message) || "加载失败" })
    } finally {
      this.setData({ loading: false })
    }
  },
  onFilterTap(e) {
    const idx = Number(e.currentTarget.dataset.index)
    if (!Number.isFinite(idx) || idx === this.data.statusIndex) return
    this.setData({ statusIndex: idx })
    this.loadList()
  },
  retry() {
    this.loadList()
  },
  goCreate() {
    wx.navigateTo({ url: "/pages/application-create/index" })
  },
  goDetail(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: `/pages/application-detail/index?id=${id}` })
  }
})
