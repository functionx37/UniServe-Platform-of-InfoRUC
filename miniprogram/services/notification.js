const { request } = require("./request")

function listNotifications(params) {
  return request({
    url: "/notifications",
    method: "GET",
    data: params || {}
  })
}

function getNotificationDetail(id) {
  return request({
    url: `/notifications/${id}`,
    method: "GET"
  })
}

function markAsRead(id) {
  return request({
    url: "/notifications/read",
    method: "POST",
    data: { id }
  })
}

module.exports = {
  listNotifications,
  getNotificationDetail,
  markAsRead
}
