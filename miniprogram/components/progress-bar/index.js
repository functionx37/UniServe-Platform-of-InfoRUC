Component({
  options: {
    addGlobalClass: true
  },
  properties: {
    label: {
      type: String,
      value: ""
    },
    percent: {
      type: Number,
      value: 0
    },
    valueText: {
      type: String,
      value: ""
    },
    showPercent: {
      type: Boolean,
      value: true
    },
    colorType: {
      type: String,
      value: "primary"
    }
  },
  data: {
    clampedPercent: 0,
    barColor: "var(--color-primary)"
  },
  observers: {
    "percent,colorType"(p, t) {
      const v = Number(p)
      const clamped = Number.isFinite(v) ? Math.max(0, Math.min(100, v)) : 0
      const type = String(t || "primary")
      if (type === "success") {
        this.setData({ clampedPercent: clamped, barColor: "var(--color-success)" })
        return
      }
      if (type === "warning") {
        this.setData({ clampedPercent: clamped, barColor: "var(--color-warning)" })
        return
      }
      if (type === "danger") {
        this.setData({ clampedPercent: clamped, barColor: "var(--color-danger)" })
        return
      }
      this.setData({ clampedPercent: clamped, barColor: "var(--color-primary)" })
    }
  }
})
