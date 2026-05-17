const env = require("../config/env")
const { getToken } = require("../utils/storage")
const { mockUploadFile } = require("./mock")

const API = {
  UPLOAD_TRANSCRIPT: "/academic/transcript/upload"
}

function uploadFile(options) {
  const useMock = Boolean(env.USE_MOCK !== undefined ? env.USE_MOCK : env.useMock)
  const baseUrl = String(env.BASE_URL || env.baseURL || "")

  if (useMock) {
    return Promise.resolve(mockUploadFile(options))
  }

  const url = options.url || ""
  const fullUrl = url.startsWith("http://") || url.startsWith("https://") ? url : `${baseUrl}${url}`
  if (!fullUrl || (!url.startsWith("http") && !baseUrl)) {
    return Promise.reject({ code: "CONFIG_ERROR", message: "未配置 BASE_URL，且未开启 USE_MOCK" })
  }

  const token = getToken()
  const header = { ...(options.header || {}) }
  if (token) header.Authorization = `Bearer ${token}`

  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: fullUrl,
      filePath: options.filePath,
      name: options.name || "file",
      formData: options.formData || {},
      header,
      success(res) {
        const statusCode = Number(res && res.statusCode) || 0
        if (statusCode < 200 || statusCode >= 300) {
          reject({ code: "HTTP_ERROR", message: `HTTP 状态码异常：${statusCode}`, statusCode, raw: res && res.data })
          return
        }
        try {
          const body = JSON.parse(res.data)
          if (body && body.success === true) {
            resolve(body)
            return
          }
          if (body && body.success === false) {
            reject({ code: "BIZ_ERROR", message: body.message || "操作失败", statusCode, raw: body })
            return
          }
          reject({ code: "INVALID_RESPONSE", message: "响应格式错误", statusCode, raw: body })
        } catch (e) {
          reject({ code: "INVALID_RESPONSE", message: "响应解析失败", statusCode, raw: null })
        }
      },
      fail(err) {
        const msg = (err && err.errMsg) || ""
        const isTimeout = msg.indexOf("timeout") >= 0
        reject({ code: isTimeout ? "TIMEOUT" : "NETWORK_ERROR", message: isTimeout ? "请求超时" : "网络错误", raw: { errMsg: msg } })
      }
    })
  })
}

function uploadTranscript({ filePath, fileName }) {
  return uploadFile({
    url: API.UPLOAD_TRANSCRIPT,
    filePath,
    name: "file",
    fileName
  })
}

module.exports = {
  API,
  uploadFile,
  uploadTranscript
}
