const { request } = require("./request")

function getMe() {
  return request({
    url: "/user/me",
    method: "GET"
  })
}

module.exports = {
  getMe
}
