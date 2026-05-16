function maskPhone(phone) {
  const s = String(phone || "")
  if (s.length < 7) return s
  return s.slice(0, 3) + "****" + s.slice(-4)
}

function maskIdCard(idCard) {
  const s = String(idCard || "")
  if (s.length < 8) return s
  return s.slice(0, 4) + "********" + s.slice(-4)
}

module.exports = {
  maskPhone,
  maskIdCard
}
