const { request } = require("./request")

function getOverview() {
  return request({
    url: "/academic/status",
    method: "GET"
  })
}

function getAnalysis() {
  return request({
    url: "/academic/analysis",
    method: "GET"
  })
}

module.exports = {
  getOverview,
  getStatus: getOverview,
  getAnalysis
}
