const template = require("../../services/template")
const { ensureLoggedIn } = require("../../utils/storage")
const env = require("../../config/env")

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
      const remoteList = (res && res.data) || []
      const list = Array.isArray(remoteList) ? remoteList.slice() : []

      const baseUrl = String(env && env.BASE_URL ? env.BASE_URL : "")
      const transcriptTemplateUrl = baseUrl ? `${baseUrl}/files/templates/student/transcript` : ""
      if (!list.some((x) => String(x && x.id) === "builtin-transcript-csv")) {
        list.unshift({
          id: "builtin-transcript-csv",
          title: "成绩单上传模板（CSV）",
          scene: "学业分析",
          fileType: "csv",
          updatedAt: "",
          directUrl: transcriptTemplateUrl
        })
      }

      if (!list.some((x) => String(x && x.id) === "builtin-example")) {
        list.push({
          id: "builtin-example",
          title: "示例模板（测试）",
          scene: "演示",
          fileType: "pdf",
          updatedAt: "",
          directUrl: "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf"
        })
      }

      this.setData({ list })
    } catch (e) {
      this.setData({ errorMsg: (e && e.message) || "加载失败" })
    } finally {
      this.setData({ loading: false })
    }
  },
  retry() {
    this.load()
  },
  isValidHttpUrl(url) {
    const u = String(url || "").trim()
    return /^https?:\/\//i.test(u)
  },
  showWaitAdmin() {
    wx.showModal({
      title: "提示",
      content: "请等待管理员上传对应模板文件",
      showCancel: false
    })
  },
  findItemById(id) {
    const list = Array.isArray(this.data.list) ? this.data.list : []
    return list.find((x) => String(x && x.id) === String(id)) || null
  },
  downloadAndOpen(url) {
    return new Promise((resolve, reject) => {
      wx.downloadFile({
        url,
        success: (r) => {
          const statusCode = Number(r && r.statusCode)
          const filePath = r && r.tempFilePath
          if (statusCode && statusCode !== 200) {
            reject(new Error("下载失败"))
            return
          }
          if (!filePath) {
            reject(new Error("下载失败"))
            return
          }
          wx.openDocument({
            filePath,
            showMenu: true,
            success: () => resolve(true),
            fail: (err) => reject(err || new Error("打开失败"))
          })
        },
        fail: (err) => reject(err || new Error("下载失败"))
      })
    })
  },
  async preview(e) {
    const id = e.currentTarget.dataset.id
    if (!id) return
    const item = this.findItemById(id)
    const directUrl = item && item.directUrl
    const url = this.isValidHttpUrl(directUrl) ? directUrl : ""
    if (!url) {
      try {
        const res = await template.previewTemplate(id)
        const apiUrl = res && res.data && res.data.url
        if (!this.isValidHttpUrl(apiUrl)) {
          this.showWaitAdmin()
          return
        }
        await this.downloadAndOpen(apiUrl)
        return
      } catch (e2) {
        this.showWaitAdmin()
        return
      }
    }
    try {
      await this.downloadAndOpen(url)
    } catch (e3) {
      this.showWaitAdmin()
    }
  },
  async download(e) {
    const id = e.currentTarget.dataset.id
    if (!id) return
    const item = this.findItemById(id)
    const directUrl = item && item.directUrl
    const url = this.isValidHttpUrl(directUrl) ? directUrl : ""
    if (!url) {
      try {
        const res = await template.downloadTemplate(id)
        const apiUrl = res && res.data && res.data.url
        if (!this.isValidHttpUrl(apiUrl)) {
          this.showWaitAdmin()
          return
        }
        await this.downloadAndOpen(apiUrl)
        return
      } catch (e2) {
        this.showWaitAdmin()
        return
      }
    }
    try {
      await this.downloadAndOpen(url)
    } catch (e3) {
      this.showWaitAdmin()
    }
  }
})
