const userService = require("../../services/user")
const party = require("../../services/party")
const application = require("../../services/application")
const { ensureLoggedIn, getUser, setUser } = require("../../utils/storage")

Page({
  data: {
    loading: false,
    errorMsg: "",
    user: null,
    userName: "—",
    todoSummary: {
      partyTodoCount: 0,
      pendingApplicationCount: 0
    },
    todoItems: []
  },
  onLoad() {
    if (!ensureLoggedIn()) return
    const cached = getUser()
    if (cached) this.setData({ user: cached })
    this.loadDashboard()
  },
  onShow() {
    if (!ensureLoggedIn()) return
    const cached = getUser()
    if (cached) {
      this.setData({ user: cached })
    }
    this.loadDashboard()
  },
  async loadDashboard() {
    if (this.data.loading) return
    this.setData({ loading: true, errorMsg: "" })
    try {
      if (!this.data.user) {
        const me = await userService.getMe()
        setUser(me.data || null)
        this.setData({ user: me.data || null })
      }

      const [pRes, aRes] = await Promise.all([party.getProgress(), application.listApplications()])
      const partyTodoCount = ((pRes.data && pRes.data.todos) || (pRes.data && pRes.data.todoTasks) || []).length
      const apps = aRes.data || []
      const pendingApplicationCount = apps.filter((x) => x && x.status === "审批中").length

      this.setData({
        userName: (this.data.user && this.data.user.displayName) || "—",
        todoSummary: { partyTodoCount, pendingApplicationCount },
        todoItems: [
          { label: "党团待办", value: partyTodoCount + " 项" },
          { label: "审批中申请", value: pendingApplicationCount + " 项" }
        ]
      })
    } catch (e) {
      this.setData({ errorMsg: (e && e.message) || "加载失败" })
    } finally {
      this.setData({ loading: false })
    }
  },
  retry() {
    this.loadDashboard()
  },
  goAi() {
    wx.switchTab({ url: "/pages/ai-chat/index" })
  },
  goParty() {
    wx.navigateTo({ url: "/pages/party-progress/index" })
  },
  goAcademic() {
    wx.navigateTo({ url: "/pages/academic/index" })
  },
  goNotifications() {
    wx.navigateTo({ url: "/pages/notifications/index" })
  },
  goTemplates() {
    wx.navigateTo({ url: "/pages/templates/index" })
  },
  goApplications() {
    wx.switchTab({ url: "/pages/applications/index" })
  }
})
