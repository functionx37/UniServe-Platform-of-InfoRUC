const { request } = require("./request")

function login(payload) {
  return request({
    url: "/auth/login",
    method: "POST",
    data: payload
  })
}

module.exports = {
  login
}
