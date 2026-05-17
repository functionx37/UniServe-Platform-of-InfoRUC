Component({
  options: {
    addGlobalClass: true
  },
  properties: {
    nodes: {
      type: Array,
      value: []
    }
  },
  data: {
    renderNodes: []
  },
  observers: {
    nodes(list) {
      const arr = Array.isArray(list) ? list : []
      const renderNodes = arr.map((n, index) => {
        const status = String((n && n.status) || "todo")
        const base = {
          key: String((n && n.key) || n && n.title ? n.title : index),
          title: (n && n.title) || "",
          desc: (n && n.desc) || "",
          time: (n && n.time) || "",
          status
        }
        if (status === "done") {
          return { ...base, dotClass: "u-tl__dot u-tl__dot--done", lineClass: "u-tl__line u-tl__line--done" }
        }
        if (status === "current") {
          return { ...base, dotClass: "u-tl__dot u-tl__dot--current", lineClass: "u-tl__line u-tl__line--todo" }
        }
        return { ...base, dotClass: "u-tl__dot u-tl__dot--todo", lineClass: "u-tl__line u-tl__line--todo" }
      })
      this.setData({ renderNodes })
    }
  }
})
