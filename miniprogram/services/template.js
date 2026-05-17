const { request } = require("./request")

function listTemplates() {
  return request({
    url: "/templates",
    method: "GET"
  })
}

function getTemplateDetail(id) {
  return request({
    url: `/templates/${id}`,
    method: "GET"
  })
}

function previewTemplate(id) {
  return request({
    url: "/templates/preview",
    method: "POST",
    data: { id }
  })
}

function downloadTemplate(id) {
  return request({
    url: "/templates/download",
    method: "POST",
    data: { id }
  })
}

module.exports = {
  listTemplates,
  getTemplateDetail,
  previewTemplate,
  downloadTemplate
}
