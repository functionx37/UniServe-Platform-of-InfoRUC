Component({
  options: {
    addGlobalClass: true
  },
  properties: {
    label: {
      type: String,
      value: "文件"
    },
    placeholder: {
      type: String,
      value: "请选择文件"
    },
    fileName: {
      type: String,
      value: ""
    },
    disabled: {
      type: Boolean,
      value: false
    },
    deletable: {
      type: Boolean,
      value: true
    },
    accept: {
      type: String,
      value: "file"
    },
    extensions: {
      type: Array,
      value: []
    },
    tip: {
      type: String,
      value: ""
    }
  },
  data: {
    innerFileName: "",
    innerFilePath: ""
  },
  observers: {
    fileName(n) {
      const name = String(n || "")
      this.setData({ innerFileName: name, innerFilePath: name ? this.data.innerFilePath : "" })
    }
  },
  methods: {
    onChoose() {
      if (this.data.disabled) return
      const type = String(this.data.accept || "file")
      const extension = Array.isArray(this.data.extensions) ? this.data.extensions : []
      this.triggerEvent("choose")
      wx.chooseMessageFile({
        count: 1,
        type,
        extension: extension.length ? extension : undefined,
        success: (res) => {
          const f = res && res.tempFiles && res.tempFiles[0]
          if (!f) return
          const name = String(f.name || "")
          const path = String(f.path || "")
          const size = Number(f.size || 0)
          this.setData({ innerFileName: name, innerFilePath: path })
          this.triggerEvent("change", { name, path, size })
        }
      })
    },
    onRemove() {
      if (!this.data.deletable || this.data.disabled) return
      this.setData({ innerFileName: "", innerFilePath: "" })
      this.triggerEvent("remove")
      this.triggerEvent("change", { name: "", path: "", size: 0 })
    }
  }
})
