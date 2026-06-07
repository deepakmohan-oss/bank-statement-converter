import { useState } from "react"

const C = { navy:"#0d1b2e", gold:"#f0b429", teal:"#0e9f8e", cream:"#f4efe6",
            grey:"#6b7280", border:"#e5e7eb", green:"#16a34a", red:"#dc2626" }
const fmt = v => v==null ? "—" : `$${Number(v).toLocaleString("en-AU",{minimumFractionDigits:2,maximumFractionDigits:2})}`

export default function ExportPage({ statement, onReset, onBack }) {
  const [loading, setLoading] = useState("")
  const [done, setDone]       = useState("")

  if (!statement) return null

  const txns   = statement.transactions ?? []
  const totalD = txns.reduce((s,t)=>s+(t.debit??0),0)
  const totalC = txns.reduce((s,t)=>s+(t.credit??0),0)
  const calcClose = statement.openingBalance != null ? statement.openingBalance + totalC - totalD : null
  const closeBal  = statement.closingBalance
  const balanced  = calcClose!=null && closeBal!=null && Math.abs(calcClose-closeBal)<0.03

  const download = async (format) => {
    setLoading(format); setDone("")
    try {
      const res = await fetch(`/api/export/${format}`, {
        method:"POST", headers:{"Content-Type":"application/json"},
        body: JSON.stringify(statement)
      })
      if (!res.ok) throw new Error(`Server error ${res.status}`)
      const blob = await res.blob()
      const url  = URL.createObjectURL(blob)
      const a    = Object.assign(document.createElement("a"), {
        href: url, download: `${statement.bank||"statement"}_transactions.${format}`
      })
      document.body.appendChild(a); a.click()
      document.body.removeChild(a); URL.revokeObjectURL(url)
      setDone(format)
    } catch(e) { alert("Export failed: "+e.message) }
    finally { setLoading("") }
  }

  return (
    <div style={{ maxWidth:640, margin:"0 auto" }}>

      {/* Account summary card */}
      <div style={{ background:"#fff", borderRadius:14, padding:"24px 28px",
        boxShadow:"0 2px 8px rgba(13,27,46,.07)", marginBottom:24, border:`1px solid ${C.border}` }}>

        <div style={{ display:"flex", alignItems:"center", gap:14, marginBottom:22 }}>
          <div style={{ width:50, height:50, borderRadius:12, background: C.navy,
            display:"flex", alignItems:"center", justifyContent:"center" }}>
            <span style={{ fontSize:22 }}>🏦</span>
          </div>
          <div>
            <div style={{ fontWeight:800, fontSize:18, color: C.navy }}>{statement.bank}</div>
            <div style={{ color: C.grey, fontSize:13 }}>
              {statement.accountName||statement.accountNumber||""}
            </div>
          </div>
          <div style={{ marginLeft:"auto" }}>
            {balanced ? (
              <span style={{ background:"#f0fdf4", color:"#166534", border:"1px solid #86efac",
                borderRadius:20, padding:"4px 14px", fontSize:12, fontWeight:700 }}>✓ BALANCED</span>
            ) : (
              <span style={{ background:"#fef2f2", color:"#991b1b", border:"1px solid #fca5a5",
                borderRadius:20, padding:"4px 14px", fontSize:12, fontWeight:700 }}>✗ CHECK</span>
            )}
          </div>
        </div>

        {/* Summary grid */}
        <div style={{ display:"grid", gridTemplateColumns:"repeat(3,1fr)", gap:14 }}>
          {[
            ["Transactions",    txns.length,                C.navy],
            ["Total Debits",    fmt(totalD),                C.red],
            ["Total Credits",   fmt(totalC),                C.green],
            ["Opening Balance", fmt(statement.openingBalance), C.grey],
            ["Closing Balance", fmt(statement.closingBalance), C.grey],
            ["Period",
              [statement.statementFrom, statement.statementTo].filter(Boolean).join(" → ")||"—",
              C.grey],
          ].map(([label,val,color]) => (
            <div key={label} style={{ background: C.cream, borderRadius:10, padding:"14px 16px" }}>
              <div style={{ color: C.grey, fontSize:11, fontWeight:700,
                textTransform:"uppercase", letterSpacing:"0.05em", marginBottom:4 }}>{label}</div>
              <div style={{ fontWeight:700, fontSize:15, color, wordBreak:"break-word" }}>{String(val)}</div>
            </div>
          ))}
        </div>
      </div>

      {/* Download */}
      <div style={{ marginBottom:28 }}>
        <div style={{ fontWeight:700, fontSize:15, color: C.navy, marginBottom:14 }}>
          Download
        </div>
        <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:14 }}>
          <button onClick={() => download("xlsx")} disabled={!!loading}
            style={{
              padding:"20px 24px", background: done==="xlsx" ? C.green : C.navy,
              color:"#fff", border:"none", borderRadius:12,
              cursor: loading ? "wait" : "pointer", fontWeight:700, fontSize:15,
              transition:"all 0.2s", opacity: loading&&loading!=="xlsx" ? 0.5 : 1,
              display:"flex", alignItems:"center", justifyContent:"center", gap:10
            }}>
            <span style={{ fontSize:22 }}>{done==="xlsx" ? "✅" : "📊"}</span>
            {loading==="xlsx" ? "Generating…" : done==="xlsx" ? "Downloaded!" : "Excel (.xlsx)"}
          </button>

          <button onClick={() => download("csv")} disabled={!!loading}
            style={{
              padding:"20px 24px",
              background: done==="csv" ? C.green : "#fff",
              color: done==="csv" ? "#fff" : C.navy,
              border: `2px solid ${done==="csv" ? C.green : C.gold}`,
              borderRadius:12, cursor: loading ? "wait" : "pointer",
              fontWeight:700, fontSize:15, transition:"all 0.2s",
              opacity: loading&&loading!=="csv" ? 0.5 : 1,
              display:"flex", alignItems:"center", justifyContent:"center", gap:10
            }}>
            <span style={{ fontSize:22 }}>{done==="csv" ? "✅" : "📋"}</span>
            {loading==="csv" ? "Generating…" : done==="csv" ? "Downloaded!" : "CSV (.csv)"}
          </button>
        </div>
        <div style={{ color: C.grey, fontSize:12, marginTop:10, textAlign:"center" }}>
          Excel includes a Summary sheet with account details, reconciliation totals, and transaction count
        </div>
      </div>

      {/* Actions */}
      <div style={{ display:"flex", gap:12, borderTop:`1px solid ${C.border}`, paddingTop:24 }}>
        <button onClick={onBack} style={{ padding:"10px 20px", border:`1px solid ${C.border}`,
          borderRadius:8, background:"#fff", cursor:"pointer", fontSize:14, color: C.grey }}>← Back</button>
        <button onClick={onReset} style={{ padding:"10px 24px", border:`2px solid ${C.gold}`,
          borderRadius:8, background:"#fff", cursor:"pointer", fontWeight:700,
          fontSize:14, color: C.navy }}>
          + Convert Another Statement
        </button>
      </div>
    </div>
  )
}
