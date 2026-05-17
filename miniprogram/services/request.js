const env = require("../config/env")
const { getToken } = require("../utils/storage")
const { mockRequest } = require("./mock")

function createError({ code, message, statusCode, raw }) {
  return {
    code: code || "UNKNOWN_ERROR",
    message: message || "请求失败",
    statusCode: statusCode || 0,
    raw: raw || null
  }
}

function normalizeUrl(url) {
  const u = String(url || "")
  if (!u) return ""
  if (u.startsWith("http://") || u.startsWith("https://")) return u
  const base = String(env.BASE_URL || env.baseURL || "")
  if (!base) return ""
  return `${base}${u}`
}

function request(options) {
  const url = String(options && options.url ? options.url : "")
  const method = String((options && options.method) || "GET").toUpperCase()
  const data = (options && options.data) || {}
  const header = (options && options.header) || {}
  const timeout = (options && options.timeout) || env.TIMEOUT || env.timeout || 10000

  const useMock = Boolean(env.USE_MOCK !== undefined ? env.USE_MOCK : env.useMock)
  if (useMock) {
    const res = mockRequest({ url, method, data, header })
    if (res && res.success) return Promise.resolve(res)
    return Promise.reject(
      createError({
        code: "BIZ_ERROR",
        message: (res && res.message) || "操作失败",
        raw: res
      })
    )
  }

  const fullUrl = normalizeUrl(url)
  if (!fullUrl) {
    return Promise.reject(
      createError({
        code: "CONFIG_ERROR",
        message: "未配置 BASE_URL，且未开启 USE_MOCK"
      })
    )
  }

  const token = getToken()
  const headers = { ...header }
  if (token) headers.Authorization = `Bearer ${token}`

  return new Promise((resolve, reject) => {
    wx.request({
      url: fullUrl,
      method,
      data,
      header: headers,
      timeout,
      success(res) {
        const statusCode = Number(res && res.statusCode) || 0
        if (statusCode < 200 || statusCode >= 300) {
          reject(
            createError({
              code: "HTTP_ERROR",
              message: `HTTP 状态码异常：${statusCode}`,
              statusCode,
              raw: res && res.data
            })
          )
          return
        }

        const body = res && res.data
        if (!body || typeof body !== "object") {
          reject(
            createError({
              code: "INVALID_RESPONSE",
              message: "响应格式错误",
              statusCode,
              raw: body
            })
          )
          return
        }

        if (body.success === true) {
          resolve(body)
          return
        }

        if (body.success === false) {
          reject(
            createError({
              code: "BIZ_ERROR",
              message: body.message || "操作失败",
              statusCode,
              raw: body
            })
          )
          return
        }

        reject(
          createError({
            code: "INVALID_RESPONSE",
            message: "响应缺少 success 字段",
            statusCode,
            raw: body
          })
        )
      },
      fail(err) {
        const msg = (err && err.errMsg) || ""
        const isTimeout = msg.indexOf("timeout") >= 0
        reject(
          createError({
            code: isTimeout ? "TIMEOUT" : "NETWORK_ERROR",
            message: isTimeout ? "请求超时" : "网络错误",
            raw: { errMsg: msg }
          })
        )
      }
    })
  })
}

module.exports = {
  request
}
