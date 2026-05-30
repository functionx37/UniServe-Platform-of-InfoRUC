const fileService = require("../../services/file")
const { ensureLoggedIn } = require("../../utils/storage")

Page({
  data: {
    filePath: "",
    fileName: "",
    fileSize: 0,
    loading: false,
    errorMsg: "",
    maxSizeMb: 10,
    allowedExt: ["pdf", "xls", "xlsx", "csv"],
    infoItems: []
  },
  onLoad() {
    if (!ensureLoggedIn()) return
    this.setData({
      infoItems: [
        { label: "大小限制", value: this.data.maxSizeMb + "MB" },
        { label: "支持格式", value: "pdf / xls / xlsx / csv" }
      ]
    })
  },
  onShow() {
    if (!ensureLoggedIn()) return
  },
  onFileChange(e) {
    const d = e.detail || {}
    const name = String(d.name || "")
    const path = String(d.path || "")
    const size = Number(d.size || 0)
    if (!name || !path) return

    const ext = name.indexOf(".") >= 0 ? name.split(".").pop().toLowerCase() : ""
    if (this.data.allowedExt.indexOf(ext) < 0) {
      this.setData({ fileName: "", filePath: "", fileSize: 0 })
      wx.showToast({ title: "文件格式错误：仅支持 PDF/Excel/CSV", icon: "none" })
      return
    }

    const maxBytes = this.data.maxSizeMb * 1024 * 1024
    if (size > maxBytes) {
      this.setData({ fileName: "", filePath: "", fileSize: 0 })
      wx.showToast({ title: `文件过大：请小于 ${this.data.maxSizeMb}MB`, icon: "none" })
      return
    }

    this.setData({ fileName: name, filePath: path, fileSize: size, errorMsg: "" })
  },
  onFileRemove() {
    this.setData({ fileName: "", filePath: "", fileSize: 0, errorMsg: "" })
  },
  async upload() {
    if (!this.data.filePath || this.data.loading) return
    this.setData({ loading: true, errorMsg: "" })
    try {
      await fileService.uploadTranscript({ filePath: this.data.filePath, fileName: this.data.fileName })
      wx.showModal({
        title: "上传成功",
        content: "成绩单已上传并完成解析，可前往查看培养方案比对结果。",
        cancelText: "继续上传",
        confirmText: "查看结果",
        success: (r) => {
          if (r.confirm) wx.navigateTo({ url: "/pages/academic-result/index" })
        }
      })
    } catch (e) {
      this.setData({ errorMsg: (e && e.message) || "上传失败" })
    } finally {
      this.setData({ loading: false })
    }
  }
})
