const { request } = require("./request")

function normalizeProgress(raw) {
  const data = raw || {}

  if (Array.isArray(data.nodes)) {
    return {
      currentStage: data.currentStage || "",
      progressPercent: Number(data.progressPercent) || 0,
      nodes: data.nodes,
      todos: Array.isArray(data.todos) ? data.todos : []
    }
  }

  const standardPath = Array.isArray(data.standardPath) ? data.standardPath : []
  const doneNodes = Array.isArray(data.doneNodes) ? data.doneNodes : []
  const currentStage = String(data.currentStage || "")

  const nodes = standardPath.map((title, index) => {
    const t = String(title || "")
    const isDone = doneNodes.indexOf(t) >= 0
    const isCurrent = !isDone && currentStage && t.indexOf(currentStage) >= 0
    return {
      key: "node-" + index,
      title: t,
      desc: "",
      time: "",
      status: isDone ? "done" : isCurrent ? "current" : "todo"
    }
  })

  const doneCount = nodes.filter((n) => n.status === "done").length
  const hasCurrent = nodes.some((n) => n.status === "current")
  const denom = nodes.length || 1
  const progressPercent = Math.round(((doneCount + (hasCurrent ? 0.5 : 0)) / denom) * 100)

  const todos = Array.isArray(data.todos)
    ? data.todos
    : Array.isArray(data.todoTasks)
      ? data.todoTasks.map((t) => ({ title: t.title || "", dueAt: t.dueAt || "", note: t.note || "" }))
      : []

  return {
    currentStage,
    progressPercent,
    nodes,
    todos
  }
}

function getPartyProgress() {
  return request({ url: "/party/progress", method: "GET" }).then((res) => {
    const normalized = normalizeProgress(res.data)
    return { ...res, data: normalized }
  })
}

module.exports = {
  getPartyProgress,
  getProgress: getPartyProgress
}
