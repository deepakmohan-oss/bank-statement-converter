import { useState } from "react"

export default function ExportPage({ statement, onReset, onBack }) {
  const [loading, setLoading] = useState("")
  const [done, setDone]       = useState("")

  if (!statement) return null

  const txns      = statement.transactions ?? []
  const totalD    = txns.reduce((s, t) => s + (t.debit  ?? 0), 0)
  const totalC    = txns.reduce((s, t) => s + (t.credit ?? 0), 0)
  const fmt       = v => v == null ? "—" : `$${Number(v).toLocaleString("en-AU",
                    { minimumFractionDigits:2, maximumFractionDigits:2 })}`

  const download = async (format) => {
    setLoading(format); setDone("")
    try {
      const res = await fetch(`/api/export/${format}`, {
        method:"POST",
        headers:{ "Content-Type":"application/json" },
        body: JSON.stringify(statement)
      })
      if (!res.ok) throw new Error(`Server error ${res.status}`)
      const blob = await res.blob()
      const url  = URL.createObjectURL(blob)
      const a    = document.createElement("a")
      a.href     = url
      a.download = `${statement.bank || "statement"}_transactions.${format}`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
      setDone(format)
    } catch (e) {
      alert("Export failed: " + e.message)
    } finally {
      setLoading("")
    }
  }

  return (
    <div style={{ maxWidth:620, margin:"0 auto" }}>

      {/* Summary card */}
      <div style={{
        background:"#fff", borderRadius:14, padding:"24px 28px",
        boxShadow:"0 1px 3px rgba(0,0,0,.07)", marginBottom:28
      }}>
        <div style={{ display:"flex", alignItems:"center", gap:14, marginBottom:20 }}>
          <div style={{
            width:48, height:48, borderRadius:12, background:"#eff6ff",
            display:"flex", alignItems:"center", justifyContent:"center", fontSize:22
          }}>🏦</div>
          <div>
            <div style={{ fontWeight:800, fontSize:18, color:"#0a1628" }}>
              {statement.bank}
            </div>
            <div style={{ color:"#64748b", fontSize:13 }}>
              {statement.accountName || statement.accountNumber || ""}
            </div>
          </div>
        </div>

        {/* Stats grid */}
        <div style={{ display:"grid", gridTemplateColumns:"repeat(3,1fr)", gap:16 }}>
          {[
            ["Transactions", txns.length, "#0a1628"],
            ["Total Debits",  fmt(totalD), "#b91c1c"],
            ["Total Credits", fmt(totalC), "#15803d"],
            ["Opening Balance", fmt(statement.openingBalance), "#475569"],
            ["Closing Balance", fmt(statement.closingBalance), "#475569"],
            ["Period", [statement.statementFrom, statement.statementTo]
              .filter(Boolean).join(" → ") || "—", "#475569"],
          ].map(([label, val, color]) => (
            <div key={label} style={{
              background:"#f8fafc", borderRadius:10, padding:"14px 16px"
            }}>
              <div style={{ color:"#94a3b8", fontSize:11, fontWeight:700,
                textTransform:"uppercase", letterSpacing:"0.05em", marginBottom:4 }}>
                {label}
              </div>
              <div style={{ fontWeight:700, fontSize:16, color, wordBreak:"break-word" }}>
                {val}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Download buttons */}
      <div style={{ marginBottom:28 }}>
        <div style={{ fontWeight:700, fontSize:15, color:"#0a1628", marginBottom:14 }}>
          Download
        </div>
        <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:14 }}>

          {/* XLSX */}
          <button onClick={() => download("xlsx")} disabled={!!loading}
            style={{
              padding:"18px 24px", background: done === "xlsx" ? "#15803d" : "#1d6fb8",
              color:"#fff", border:"none", borderRadius:12, cursor: loading ? "wait" : "pointer",
              fontWeight:700, fontSize:15, transition:"all 0.2s",
              opacity: loading && loading !== "xlsx" ? 0.5 : 1,
              display:"flex", alignItems:"center", justifyContent:"center", gap:10
            }}>
            <span style={{ fontSize:22 }}>{done === "xlsx" ? "✅" : "📊"}</span>
            {loading === "xlsx" ? "Generating…" : done === "xlsx" ? "Downloaded!" : "Excel (.xlsx)"}
          </button>

          {/* CSV */}
          <button onClick={() => download("csv")} disabled={!!loading}
            style={{
              padding:"18px 24px",
              background: done === "csv" ? "#15803d" : "#fff",
              color: done === "csv" ? "#fff" : "#0a1628",
              border: done === "csv" ? "2px solid #15803d" : "2px solid #e2e8f0",
              borderRadius:12, cursor: loading ? "wait" : "pointer",
              fontWeight:700, fontSize:15, transition:"all 0.2s",
              opacity: loading && loading !== "csv" ? 0.5 : 1,
              display:"flex", alignItems:"center", justifyContent:"center", gap:10
            }}>
            <span style={{ fontSize:22 }}>{done === "csv" ? "✅" : "📋"}</span>
            {loading === "csv" ? "Generating…" : done === "csv" ? "Downloaded!" : "CSV (.csv)"}
          </button>
        </div>

        <div style={{ color:"#94a3b8", fontSize:12, marginTop:10, textAlign:"center" }}>
          Excel includes a Summary sheet with account details and totals
        </div>
      </div>

      {/* Actions */}
      <div style={{ display:"flex", gap:12, borderTop:"1px solid #f1f5f9", paddingTop:24 }}>
        <button onClick={onBack} style={{
          padding:"10px 20px", border:"1px solid #e2e8f0", borderRadius:8,
          background:"#fff", cursor:"pointer", fontSize:14, color:"#64748b"
        }}>← Back</button>
        <button onClick={onReset} style={{
          padding:"10px 20px", border:"1px solid #e2e8f0", borderRadius:8,
          background:"#fff", cursor:"pointer", fontSize:14, color:"#1d6fb8", fontWeight:600
        }}>+ Convert Another Statement</button>
      </div>
    </div>
  )
}
