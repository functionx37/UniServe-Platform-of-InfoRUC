const userService = require("../../services/user")
const { ensureLoggedIn, getToken, getUser, clearToken, clearUser, setUser } = require("../../utils/storage")
const { maskPhone, maskIdCard } = require("../../utils/mask")

Page({
  data: {
    token: "",
    user: null,
    maskedPhone: "",
    maskedIdCard: "",
    loading: false,
    errorMsg: "",
    basicItems: [],
    contactItems: []
  },
  onLoad() {
    if (!ensureLoggedIn()) return
  },
  onShow() {
    const token = getToken()
    this.setData({ token })
    if (!ensureLoggedIn()) return

    const cached = getUser()
    if (cached) {
      this.applyUser(cached)
      return
    }
    this.loadMe()
  },
  applyUser(user) {
    const u = user || {}
    const phone = maskPhone(u.phone)
    const idCard = maskIdCard(u.idCard)
    this.setData({
      user: u,
      maskedPhone: phone,
      maskedIdCard: idCard,
      basicItems: [
        { label: "姓名", value: u.displayName || "—" },
        { label: "学号", value: u.studentNo || "—" },
        { label: "学院", value: u.major || "—" },
        { label: "年级", value: u.grade || "—" }
      ],
      contactItems: [
        { label: "手机号", value: phone || "—" },
        { label: "身份证号", value: idCard || "—" }
      ]
    })
  },
  async loadMe() {
    if (this.data.loading) return
    this.setData({ loading: true, errorMsg: "" })
    try {
      const res = await userService.getMe()
      const user = res.data || {}
      setUser(user)
      this.applyUser(user)
    } catch (e) {
      this.setData({ errorMsg: (e && e.message) || "获取信息失败" })
    } finally {
      this.setData({ loading: false })
    }
  },
  goLogin() {
    wx.navigateTo({ url: "/pages/login/index" })
  },
  goApplications() {
    wx.switchTab({ url: "/pages/applications/index" })
  },
  goUploads() {
    wx.navigateTo({ url: "/pages/transcript-upload/index" })
  },
  logout() {
    clearToken()
    clearUser()
    this.setData({ token: "", user: null, maskedPhone: "", maskedIdCard: "", errorMsg: "" })
    wx.showToast({ title: "已退出", icon: "success" })
    wx.switchTab({ url: "/pages/index/index" })
  }
})
