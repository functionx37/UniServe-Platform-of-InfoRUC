const { request } = require("./request")

function askQuestion(input) {
  const question = typeof input === "string" ? input : input && input.question
  return request({
    url: "/ai/ask",
    method: "POST",
    data: { question }
  })
}

module.exports = {
  askQuestion,
  ask: askQuestion
}
