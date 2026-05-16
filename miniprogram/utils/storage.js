const { STORAGE_KEYS } = require("./constants")

function setToken(token) {
  wx.setStorageSync(STORAGE_KEYS.TOKEN, token || "")
}

function getToken() {
  return wx.getStorageSync(STORAGE_KEYS.TOKEN) || ""
}

function clearToken() {
  wx.removeStorageSync(STORAGE_KEYS.TOKEN)
}

function setUser(user) {
  wx.setStorageSync(STORAGE_KEYS.USER, user || null)
}

function getUser() {
  return wx.getStorageSync(STORAGE_KEYS.USER) || null
}

function clearUser() {
  wx.removeStorageSync(STORAGE_KEYS.USER)
}

function isLoggedIn() {
  return !!getToken()
}

function ensureLoggedIn() {
  if (isLoggedIn()) return true
  wx.navigateTo({ url: "/pages/login/index" })
  return false
}

module.exports = {
  setToken,
  getToken,
  clearToken,
  setUser,
  getUser,
  clearUser,
  isLoggedIn,
  ensureLoggedIn
}
