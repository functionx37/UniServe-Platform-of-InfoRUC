Component({
  options: {
    addGlobalClass: true
  },
  properties: {
    title: {
      type: String,
      value: "加载失败"
    },
    message: {
      type: String,
      value: ""
    },
    retryText: {
      type: String,
      value: "重试"
    },
    showRetry: {
      type: Boolean,
      value: true
    }
  },
  methods: {
    onRetry() {
      this.triggerEvent("retry")
    }
  }
})
