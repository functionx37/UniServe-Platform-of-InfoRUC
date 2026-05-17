const auth = require("../../services/auth")
const { setToken, setUser } = require("../../utils/storage")

Page({
  data: {
    studentNo: "",
    name: "",
    loading: false,
    errorMsg: ""
  },
  onStudentNo(e) {
    this.setData({ studentNo: e.detail.value })
  },
  onName(e) {
    this.setData({ name: e.detail.value })
  },
  async onLogin() {
    if (this.data.loading) return
    this.setData({ loading: true, errorMsg: "" })
    try {
      const res = await auth.login({
        studentNo: this.data.studentNo,
        name: this.data.name
      })
      const data = (res && res.data) || {}
      setToken(data.token)
      if (data.userInfo) setUser(data.userInfo)
      wx.showToast({ title: (res && res.message) || "登录成功", icon: "success" })
      wx.switchTab({ url: "/pages/index/index" })
    } catch (e) {
      this.setData({ errorMsg: (e && e.message) || "登录失败" })
    } finally {
      this.setData({ loading: false })
    }
  }
})
