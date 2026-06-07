import { useState, useMemo } from "react"

const C = { navy:"#0d1b2e", navyL:"#162840", gold:"#f0b429", teal:"#0e9f8e",
            cream:"#f4efe6", grey:"#6b7280", red:"#dc2626", green:"#16a34a",
            border:"#e5e7eb", rowAlt:"#faf8f5" }
const fmt = v => v == null ? "" : `$${Number(v).toLocaleString("en-AU",{minimumFractionDigits:2,maximumFractionDigits:2})}`

export default function ReviewPage({ statement, onNext, onBack }) {
  const [search, setSearch]   = useState("")
  const [sortCol, setSortCol] = useState("date")
  const [sortDir, setSortDir] = useState("asc")
  const [page, setPage]       = useState(1)
  const PAGE_SIZE = 50

  const txns    = statement?.transactions ?? []
  const totalD  = txns.reduce((s,t)=>s+(t.debit??0),0)
  const totalC  = txns.reduce((s,t)=>s+(t.credit??0),0)
  const netFlow = totalC - totalD

  // Reconciliation check
  const openBal  = statement?.openingBalance
  const closeBal = statement?.closingBalance
  const calcClose = openBal != null ? openBal + totalC - totalD : null
  const balanced  = calcClose != null && closeBal != null && Math.abs(calcClose - closeBal) < 0.03
  const lastBal   = txns.length ? txns[txns.length-1].balance : null
  const lastMatch = lastBal != null && closeBal != null && Math.abs(lastBal - closeBal) < 0.03

  const filtered = useMemo(() => {
    let rows = txns
    if (search.trim()) {
      const q = search.toLowerCase()
      rows = rows.filter(t =>
        t.date?.includes(q) || t.description?.toLowerCase().includes(q) ||
        String(t.debit??'').includes(q) || String(t.credit??'').includes(q)
      )
    }
    return [...rows].sort((a,b)=>{
      const va=a[sortCol]??0, vb=b[sortCol]??0
      const cmp = typeof va==='number' ? va-vb : String(va).localeCompare(String(vb))
      return sortDir==='asc' ? cmp : -cmp
    })
  }, [txns, search, sortCol, sortDir])

  const pages    = Math.max(1,Math.ceil(filtered.length/PAGE_SIZE))
  const pageRows = filtered.slice((page-1)*PAGE_SIZE, page*PAGE_SIZE)

  const sort = col => {
    if (sortCol===col) setSortDir(d=>d==='asc'?'desc':'asc')
    else { setSortCol(col); setSortDir('asc') }
    setPage(1)
  }
  const arrow = col => sortCol===col ? (sortDir==='asc'?' ↑':' ↓') : ''

  if (!statement) return null

  const TH = ({ col, label, right }) => (
    <th onClick={() => sort(col)} style={{
      padding:"10px 14px", background: C.navy, color:"#fff", fontSize:12,
      fontWeight:700, cursor:"pointer", textAlign: right?"right":"left",
      whiteSpace:"nowrap", userSelect:"none", borderRight:`1px solid ${C.navyL}`
    }}>{label}{arrow(col)}</th>
  )

  return (
    <div>
      {/* ── Reconciliation Summary (matching Python app) ─────────────── */}
      <div style={{
        background:"#fff", borderRadius:14, padding:"20px 24px", marginBottom:20,
        boxShadow:"0 2px 8px rgba(13,27,46,.07)", border:`1px solid ${C.border}`
      }}>
        <div style={{ display:"flex", alignItems:"center", gap:12, marginBottom:18 }}>
          <div style={{ fontWeight:800, fontSize:16, color: C.navy }}>
            Reconciliation Summary
          </div>
          {balanced && lastMatch ? (
            <span style={{ background:"#f0fdf4", color:"#166534", border:"1px solid #86efac",
              borderRadius:20, padding:"2px 12px", fontSize:12, fontWeight:700 }}>
              ✓ BALANCED
            </span>
          ) : (
            <span style={{ background:"#fef2f2", color:"#991b1b", border:"1px solid #fca5a5",
              borderRadius:20, padding:"2px 12px", fontSize:12, fontWeight:700 }}>
              ✗ CHECK REQUIRED
            </span>
          )}
        </div>

        <div style={{ display:"grid", gridTemplateColumns:"repeat(auto-fit,minmax(150px,1fr))", gap:14 }}>
          {[
            ["Bank",            statement.bank,         C.navy],
            ["Account",         statement.accountNumber||"—", C.navy],
            ["Transactions",    txns.length,            C.navy],
            ["Opening Balance", fmt(openBal),           C.navy],
            ["Total Credits",   fmt(totalC),            C.green],
            ["Total Debits",    fmt(totalD),            C.red],
            ["Net Flow",        fmt(netFlow),           netFlow>=0?C.green:C.red],
            ["Calc. Closing",   fmt(calcClose),         C.navy],
            ["Stated Closing",  fmt(closeBal),          C.navy],
          ].map(([label, val, color]) => (
            <div key={label} style={{
              background: C.cream, borderRadius:10, padding:"12px 16px"
            }}>
              <div style={{ color: C.grey, fontSize:11, fontWeight:700,
                textTransform:"uppercase", letterSpacing:"0.05em", marginBottom:4 }}>
                {label}
              </div>
              <div style={{ fontWeight:700, fontSize:15, color, wordBreak:"break-word" }}>
                {String(val)}
              </div>
            </div>
          ))}
        </div>

        {/* Balance check detail */}
        {(calcClose != null && closeBal != null) && (
          <div style={{
            marginTop:14, padding:"10px 16px", borderRadius:8, fontSize:13,
            background: balanced ? "#f0fdf4" : "#fef2f2",
            color: balanced ? "#166534" : "#991b1b",
            border: `1px solid ${balanced ? "#86efac" : "#fca5a5"}`
          }}>
            {balanced
              ? `✓ Opening ${fmt(openBal)} + Credits ${fmt(totalC)} − Debits ${fmt(totalD)} = ${fmt(calcClose)} matches stated closing ${fmt(closeBal)}`
              : `✗ Calculated closing ${fmt(calcClose)} differs from stated ${fmt(closeBal)} by ${fmt(Math.abs(calcClose-closeBal))}`
            }
          </div>
        )}
      </div>

      {/* ── Search ───────────────────────────────────────────────────── */}
      <div style={{ position:"relative", marginBottom:14 }}>
        <span style={{ position:"absolute", left:14, top:"50%", transform:"translateY(-50%)",
          color:"#aaa", pointerEvents:"none" }}>🔍</span>
        <input placeholder="Search transactions…" value={search}
          onChange={e => { setSearch(e.target.value); setPage(1) }}
          style={{ width:"100%", padding:"10px 14px 10px 40px", border:`1px solid ${C.border}`,
            borderRadius:8, fontSize:14, boxSizing:"border-box", background:"#fff",
            boxShadow:"0 1px 2px rgba(0,0,0,.04)" }} />
        {search && (
          <button onClick={() => { setSearch(""); setPage(1) }}
            style={{ position:"absolute", right:12, top:"50%", transform:"translateY(-50%)",
              border:"none", background:"none", cursor:"pointer", color:"#aaa", fontSize:18 }}>×</button>
        )}
      </div>

      {/* ── Transaction table ────────────────────────────────────────── */}
      <div style={{ background:"#fff", borderRadius:12, overflow:"hidden",
        boxShadow:"0 2px 8px rgba(13,27,46,.07)", marginBottom:20,
        border:`1px solid ${C.border}` }}>
        <div style={{ overflowX:"auto" }}>
          <table style={{ width:"100%", borderCollapse:"collapse", fontSize:13 }}>
            <thead>
              <tr>
                <TH col="date"        label="Date" />
                <TH col="description" label="Description" />
                <TH col="debit"       label="Debit ($)"   right />
                <TH col="credit"      label="Credit ($)"  right />
                <TH col="balance"     label="Balance ($)" right />
              </tr>
            </thead>
            <tbody>
              {pageRows.map((t, i) => (
                <tr key={i} style={{
                  background: i%2===0 ? "#fff" : C.rowAlt,
                  borderBottom:`1px solid ${C.border}`
                }}>
                  <td style={{ padding:"9px 14px", whiteSpace:"nowrap", fontVariantNumeric:"tabular-nums" }}>
                    {t.date}
                  </td>
                  <td style={{ padding:"9px 14px", maxWidth:320, overflow:"hidden",
                    textOverflow:"ellipsis", whiteSpace:"nowrap" }} title={t.description}>
                    {t.description}
                  </td>
                  <td style={{ padding:"9px 14px", textAlign:"right", color: C.red,
                    fontVariantNumeric:"tabular-nums" }}>
                    {t.debit!=null ? t.debit.toFixed(2) : ""}
                  </td>
                  <td style={{ padding:"9px 14px", textAlign:"right", color: C.green,
                    fontVariantNumeric:"tabular-nums" }}>
                    {t.credit!=null ? t.credit.toFixed(2) : ""}
                  </td>
                  <td style={{ padding:"9px 14px", textAlign:"right", fontWeight:500,
                    fontVariantNumeric:"tabular-nums" }}>
                    {t.balance!=null ? t.balance.toFixed(2) : ""}
                  </td>
                </tr>
              ))}
              {pageRows.length===0 && (
                <tr><td colSpan={5} style={{ padding:32, textAlign:"center", color: C.grey }}>
                  No transactions match your search.
                </td></tr>
              )}
            </tbody>
          </table>
        </div>

        {pages > 1 && (
          <div style={{ padding:"12px 16px", borderTop:`1px solid ${C.border}`,
            display:"flex", alignItems:"center", gap:8, fontSize:13, color: C.grey }}>
            <span>Page {page} of {pages} · {filtered.length} results</span>
            <div style={{ marginLeft:"auto", display:"flex", gap:6 }}>
              {[["←",page>1,()=>setPage(p=>p-1)],["→",page<pages,()=>setPage(p=>p+1)]].map(([l,en,fn],i)=>(
                <button key={i} onClick={fn} disabled={!en} style={{
                  padding:"4px 12px", borderRadius:6, border:`1px solid ${C.border}`,
                  background: en?"#fff":C.rowAlt, cursor: en?"pointer":"default",
                  color: en? C.navy:"#ccc"
                }}>{l}</button>
              ))}
            </div>
          </div>
        )}
      </div>

      <div style={{ display:"flex", gap:12 }}>
        <button onClick={onBack} style={{ padding:"10px 20px", border:`1px solid ${C.border}`,
          borderRadius:8, background:"#fff", cursor:"pointer", fontSize:14, color: C.grey }}>
          ← Back
        </button>
        <button onClick={onNext} style={{ padding:"10px 28px", background: C.gold, color: C.navy,
          border:"none", borderRadius:8, cursor:"pointer", fontWeight:700, fontSize:14 }}>
          Check Duplicates →
        </button>
      </div>
    </div>
  )
}
