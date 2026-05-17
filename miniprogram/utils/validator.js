function required(value) {
  if (value === 0) return true
  return !!(value && String(value).trim())
}

module.exports = {
  required
}
