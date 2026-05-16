Component({
  options: {
    addGlobalClass: true
  },
  properties: {
    title: {
      type: String,
      value: ""
    },
    statusText: {
      type: String,
      value: ""
    },
    statusType: {
      type: String,
      value: "info"
    },
    description: {
      type: String,
      value: ""
    },
    items: {
      type: Array,
      value: []
    }
  },
  data: {
    tagClass: "u-tag"
  },
  observers: {
    statusType(t) {
      const type = String(t || "info")
      if (type === "success") {
        this.setData({ tagClass: "u-tag u-tag--success" })
        return
      }
      if (type === "warning") {
        this.setData({ tagClass: "u-tag u-tag--warning" })
        return
      }
      if (type === "danger") {
        this.setData({ tagClass: "u-tag u-tag--danger" })
        return
      }
      this.setData({ tagClass: "u-tag" })
    }
  }
})
