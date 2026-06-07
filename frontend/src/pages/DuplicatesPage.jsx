import { useState, useEffect } from "react"

const C = { navy:"#0d1b2e", gold:"#f0b429", teal:"#0e9f8e", cream:"#f4efe6",
            grey:"#6b7280", border:"#e5e7eb", amber:"#fef3c7", amberBorder:"#fcd34d" }

export default function DuplicatesPage({ statement, onConfirm, onBack }) {
  const [dupes, setDupes]   = useState([])
  const [remove, setRemove] = useState({})

  useEffect(() => {
    if (!statement?.transactions) return
    const seen = new Map(), found = []
    statement.transactions.forEach((t, i) => {
      const key = `${t.date}|${t.description?.trim().toUpperCase()}|${t.debit}|${t.credit}`
      if (seen.has(key)) found.push({ ...t, index: i })
      else seen.set(key, i)
    })
    setDupes(found)
    setRemove(Object.fromEntries(found.map((_,i)=>[i,true])))
  }, [statement])

  const confirm = () => {
    const removeIdx = new Set(dupes.filter((_,i)=>remove[i]).map(d=>d.index))
    onConfirm({ ...statement,
      transactions: statement.transactions.filter((_,i)=>!removeIdx.has(i)) })
  }

  const selectedCount = Object.values(remove).filter(Boolean).length

  if (!statement) return null

  if (dupes.length === 0) return (
    <div style={{ maxWidth:560, margin:"0 auto" }}>
      <div style={{ background:"#f0fdf4", border:"1px solid #86efac", borderRadius:14,
        padding:"32px", textAlign:"center", marginBottom:24 }}>
        <div style={{ fontSize:44, marginBottom:12 }}>✅</div>
        <div style={{ fontWeight:800, fontSize:18, color:"#166534", marginBottom:6 }}>
          No duplicates found
        </div>
        <div style={{ color:"#15803d", fontSize:14 }}>
          All {statement.transactions.length} transactions are unique.
        </div>
      </div>
      <div style={{ display:"flex", gap:12 }}>
        <button onClick={onBack} style={{ padding:"10px 20px", border:`1px solid ${C.border}`,
          borderRadius:8, background:"#fff", cursor:"pointer", fontSize:14, color: C.grey }}>← Back</button>
        <button onClick={() => onConfirm(statement)} style={{ padding:"10px 28px", background: C.gold,
          color: C.navy, border:"none", borderRadius:8, cursor:"pointer", fontWeight:700, fontSize:14 }}>
          Continue to Export →
        </button>
      </div>
    </div>
  )

  return (
    <div>
      {/* Warning bar */}
      <div style={{ background: C.amber, border:`1px solid ${C.amberBorder}`, borderRadius:12,
        padding:"16px 20px", marginBottom:20, display:"flex", alignItems:"center", gap:16 }}>
        <span style={{ fontSize:24 }}>⚠️</span>
        <div>
          <div style={{ fontWeight:700, color:"#92400e", fontSize:15 }}>
            {dupes.length} duplicate transaction{dupes.length!==1?"s":""} detected
          </div>
          <div style={{ color:"#a16207", fontSize:13, marginTop:2 }}>
            Highlighted rows appear to be duplicates. Select which to remove before export.
          </div>
        </div>
        <div style={{ marginLeft:"auto", display:"flex", gap:8 }}>
          <button onClick={() => setRemove(Object.fromEntries(dupes.map((_,i)=>[i,true])))}
            style={{ padding:"5px 12px", fontSize:12, border:`1px solid ${C.amberBorder}`,
              borderRadius:6, background:"#fff", cursor:"pointer", color:"#92400e" }}>Select all</button>
          <button onClick={() => setRemove(Object.fromEntries(dupes.map((_,i)=>[i,false])))}
            style={{ padding:"5px 12px", fontSize:12, border:`1px solid ${C.border}`,
              borderRadius:6, background:"#fff", cursor:"pointer", color: C.grey }}>None</button>
        </div>
      </div>

      {/* Table */}
      <div style={{ background:"#fff", borderRadius:12, overflow:"hidden",
        boxShadow:"0 2px 8px rgba(13,27,46,.07)", marginBottom:20, border:`1px solid ${C.border}` }}>
        <div style={{ overflowX:"auto" }}>
          <table style={{ width:"100%", borderCollapse:"collapse" }}>
            <thead>
              <tr>
                {["Remove","Date","Description","Debit","Credit","Balance"].map(h => (
                  <th key={h} style={{ padding:"10px 14px", background: C.navy, color:"#fff",
                    fontWeight:700, fontSize:12, textAlign: h==="Remove"?"center":"left" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {dupes.map((t, i) => (
                <tr key={i} style={{
                  background: remove[i] ? C.amber : "#fff",
                  borderBottom:`1px solid ${C.border}`, transition:"background 0.15s"
                }}>
                  <td style={{ padding:"9px 14px", textAlign:"center" }}>
                    <input type="checkbox" checked={!!remove[i]}
                      onChange={e => setRemove(p=>({...p,[i]:e.target.checked}))}
                      style={{ cursor:"pointer", width:15, height:15 }} />
                  </td>
                  <td style={{ padding:"9px 14px", whiteSpace:"nowrap" }}>{t.date}</td>
                  <td style={{ padding:"9px 14px", maxWidth:280, overflow:"hidden",
                    textOverflow:"ellipsis", whiteSpace:"nowrap" }} title={t.description}>
                    {t.description}
                  </td>
                  <td style={{ padding:"9px 14px", color:"#dc2626" }}>{t.debit?.toFixed(2)??""}</td>
                  <td style={{ padding:"9px 14px", color:"#16a34a" }}>{t.credit?.toFixed(2)??""}</td>
                  <td style={{ padding:"9px 14px" }}>{t.balance?.toFixed(2)??""}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div style={{ display:"flex", gap:12, alignItems:"center" }}>
        <button onClick={onBack} style={{ padding:"10px 20px", border:`1px solid ${C.border}`,
          borderRadius:8, background:"#fff", cursor:"pointer", fontSize:14, color: C.grey }}>← Back</button>
        <button onClick={confirm} style={{ padding:"10px 28px", background: C.gold, color: C.navy,
          border:"none", borderRadius:8, cursor:"pointer", fontWeight:700, fontSize:14 }}>
          {selectedCount>0 ? `Remove ${selectedCount} & Export →` : "Continue to Export →"}
        </button>
        {selectedCount>0 && (
          <span style={{ color: C.grey, fontSize:13 }}>
            {statement.transactions.length - selectedCount} transactions remaining
          </span>
        )}
      </div>
    </div>
  )
}
