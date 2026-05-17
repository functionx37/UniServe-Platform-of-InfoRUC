Component({
  options: {
    addGlobalClass: true
  },
  properties: {
    title: {
      type: String,
      value: ""
    },
    subtitle: {
      type: String,
      value: ""
    },
    iconText: {
      type: String,
      value: ""
    },
    rightText: {
      type: String,
      value: ""
    },
    disabled: {
      type: Boolean,
      value: false
    }
  },
  methods: {
    onTap() {
      if (this.data.disabled) return
      this.triggerEvent("tap")
    }
  }
})
