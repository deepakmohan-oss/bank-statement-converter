import { useState } from "react"

export default function ExportPage({ statement, onStartOver }) {
  const [loading, setLoading] = useState("")

  if (!statement) return <div style={{ padding: 40 }}>No statement loaded.</div>

  const download = async (format) => {
    setLoading(format)
    try {
      const res = await fetch(`/api/export/${format}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(statement)
      })
      if (!res.ok) throw new Error("Export failed")
      const blob = await res.blob()
      const url  = URL.createObjectURL(blob)
      const a    = document.createElement("a")
      a.href     = url
      a.download = `${statement.bank}_transactions.${format}`
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      alert("Export failed: " + e.message)
    } finally {
      setLoading("")
    }
  }

  const txns = statement.transactions ?? []
  const totalDebits  = txns.reduce((s, t) => s + (t.debit  ?? 0), 0)
  const totalCredits = txns.reduce((s, t) => s + (t.credit ?? 0), 0)

  const btn = (label, fmt, colour) => (
    <button
      onClick={() => download(fmt)}
      disabled={!!loading}
      style={{
        padding: "14px 28px", background: colour, color: "#fff",
        border: "none", borderRadius: 10, cursor: loading ? "wait" : "pointer",
        fontWeight: 700, fontSize: 15, opacity: loading && loading !== fmt ? 0.5 : 1
      }}
    >
      {loading === fmt ? "Generating…" : label}
    </button>
  )

  return (
    <div style={{ maxWidth: 560, margin: "60px auto", fontFamily: "system-ui, sans-serif" }}>
      <h2 style={{ fontSize: 22, fontWeight: 700, marginBottom: 4 }}>Export</h2>

      {/* Summary card */}
      <div style={{
        background: "#f0f9ff", border: "1px solid #bae6fd",
        borderRadius: 10, padding: "18px 22px", marginBottom: 32
      }}>
        <div style={{ fontWeight: 700, marginBottom: 8 }}>{statement.bank} — {statement.accountNumber}</div>
        <div style={{ fontSize: 13, color: "#0369a1" }}>
          {txns.length} transactions
          {statement.statementFrom && ` · ${statement.statementFrom} → ${statement.statementTo}`}
        </div>
        <div style={{ fontSize: 13, color: "#0369a1", marginTop: 4 }}>
          Debits: ${totalDebits.toFixed(2)} · Credits: ${totalCredits.toFixed(2)}
          {statement.closingBalance != null && ` · Closing: $${statement.closingBalance.toFixed(2)}`}
        </div>
      </div>

      <div style={{ display: "flex", gap: 16, marginBottom: 32 }}>
        {btn("⬇ Download CSV",  "csv",  "#16a34a")}
        {btn("⬇ Download XLSX", "xlsx", "#2563eb")}
      </div>

      <button onClick={onStartOver} style={{
        padding: "9px 18px", background: "none", color: "#64748b",
        border: "1px solid #cbd5e1", borderRadius: 8, cursor: "pointer", fontSize: 13
      }}>
        ← Convert another statement
      </button>
    </div>
  )
}
