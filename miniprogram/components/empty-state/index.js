Component({
  options: {
    addGlobalClass: true
  },
  properties: {
    title: {
      type: String,
      value: "暂无数据"
    },
    description: {
      type: String,
      value: ""
    },
    iconText: {
      type: String,
      value: "—"
    },
    actionText: {
      type: String,
      value: ""
    },
    showAction: {
      type: Boolean,
      value: false
    }
  },
  methods: {
    onAction() {
      this.triggerEvent("action")
    }
  }
})
