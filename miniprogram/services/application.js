const { request } = require("./request")

function listApplications(params) {
  return request({
    url: "/applications",
    method: "GET",
    data: params || {}
  })
}

function getApplicationDetail(id) {
  return request({
    url: `/applications/${id}`,
    method: "GET"
  })
}

function createApplication(payload) {
  return request({
    url: "/applications",
    method: "POST",
    data: payload
  })
}

module.exports = {
  listApplications,
  getApplicationDetail,
  createApplication
}
