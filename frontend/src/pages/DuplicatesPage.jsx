import { useState, useEffect } from "react"

export default function DuplicatesPage({ statement, onConfirm, onBack }) {
  const [dupes, setDupes]   = useState([])
  const [remove, setRemove] = useState({})

  useEffect(() => {
    if (!statement?.transactions) return
    const seen = new Map()
    const found = []
    statement.transactions.forEach((t, i) => {
      const key = `${t.date}|${t.description?.trim().toUpperCase()}|${t.debit}|${t.credit}`
      if (seen.has(key)) found.push({ ...t, index: i })
      else seen.set(key, i)
    })
    setDupes(found)
    setRemove(Object.fromEntries(found.map((_, i) => [i, true])))
  }, [statement])

  const confirm = () => {
    const removeIdxs = new Set(dupes.filter((_, i) => remove[i]).map(d => d.index))
    onConfirm({ ...statement,
      transactions: statement.transactions.filter((_, i) => !removeIdxs.has(i)) })
  }

  const COL = { padding:"10px 12px", fontSize:13 }
  const HDR = { ...COL, background:"#0a1628", color:"#fff", fontWeight:600, textAlign:"left" }

  if (!statement) return null

  if (dupes.length === 0) return (
    <div style={{ maxWidth:560, margin:"0 auto" }}>
      <div style={{
        background:"#f0fdf4", border:"1px solid #86efac", borderRadius:12,
        padding:"28px 32px", textAlign:"center", marginBottom:24
      }}>
        <div style={{ fontSize:40, marginBottom:12 }}>✅</div>
        <div style={{ fontWeight:700, fontSize:17, color:"#166534", marginBottom:4 }}>
          No duplicates found
        </div>
        <div style={{ color:"#15803d", fontSize:14 }}>
          All {statement.transactions.length} transactions are unique.
        </div>
      </div>
      <div style={{ display:"flex", gap:12 }}>
        <button onClick={onBack} style={{
          padding:"10px 20px", border:"1px solid #e2e8f0", borderRadius:8,
          background:"#fff", cursor:"pointer", fontSize:14, color:"#64748b"
        }}>← Back</button>
        <button onClick={() => onConfirm(statement)} style={{
          padding:"10px 24px", background:"#1d6fb8", color:"#fff",
          border:"none", borderRadius:8, cursor:"pointer", fontWeight:600, fontSize:14
        }}>Continue to Export →</button>
      </div>
    </div>
  )

  const selectedCount = Object.values(remove).filter(Boolean).length

  return (
    <div>
      <div style={{
        background:"#fffbeb", border:"1px solid #fcd34d", borderRadius:12,
        padding:"16px 20px", marginBottom:20, display:"flex",
        alignItems:"center", gap:16
      }}>
        <span style={{ fontSize:24 }}>⚠️</span>
        <div>
          <div style={{ fontWeight:700, color:"#92400e", fontSize:15 }}>
            {dupes.length} duplicate transaction{dupes.length !== 1 ? "s" : ""} detected
          </div>
          <div style={{ color:"#a16207", fontSize:13, marginTop:2 }}>
            Selected duplicates will be removed before export.
          </div>
        </div>
        <div style={{ marginLeft:"auto", display:"flex", gap:8 }}>
          <button onClick={() => setRemove(Object.fromEntries(dupes.map((_,i)=>[i,true])))}
            style={{ padding:"5px 12px", fontSize:12, border:"1px solid #fcd34d",
              borderRadius:6, background:"#fff", cursor:"pointer", color:"#92400e" }}>
            Select all
          </button>
          <button onClick={() => setRemove(Object.fromEntries(dupes.map((_,i)=>[i,false])))}
            style={{ padding:"5px 12px", fontSize:12, border:"1px solid #e2e8f0",
              borderRadius:6, background:"#fff", cursor:"pointer", color:"#64748b" }}>
            None
          </button>
        </div>
      </div>

      <div style={{ background:"#fff", borderRadius:12, overflow:"hidden",
        boxShadow:"0 1px 3px rgba(0,0,0,.06)", marginBottom:20 }}>
        <div style={{ overflowX:"auto" }}>
          <table style={{ width:"100%", borderCollapse:"collapse" }}>
            <thead>
              <tr>
                <th style={{ ...HDR, width:44, textAlign:"center" }}>✕</th>
                {["Date","Description","Debit","Credit","Balance"].map(h => (
                  <th key={h} style={HDR}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {dupes.map((t, i) => (
                <tr key={i} style={{
                  background: remove[i] ? "#fef2f2" : "#fff",
                  borderBottom:"1px solid #f1f5f9", transition:"background 0.15s"
                }}>
                  <td style={{ ...COL, textAlign:"center" }}>
                    <input type="checkbox" checked={!!remove[i]}
                      onChange={e => setRemove(p => ({ ...p, [i]: e.target.checked }))}
                      style={{ cursor:"pointer", width:15, height:15 }} />
                  </td>
                  <td style={{ ...COL, whiteSpace:"nowrap" }}>{t.date}</td>
                  <td style={{ ...COL, maxWidth:280, overflow:"hidden", textOverflow:"ellipsis",
                    whiteSpace:"nowrap" }} title={t.description}>{t.description}</td>
                  <td style={{ ...COL, color:"#b91c1c" }}>{t.debit?.toFixed(2) ?? ""}</td>
                  <td style={{ ...COL, color:"#15803d" }}>{t.credit?.toFixed(2) ?? ""}</td>
                  <td style={COL}>{t.balance?.toFixed(2) ?? ""}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div style={{ display:"flex", gap:12, alignItems:"center" }}>
        <button onClick={onBack} style={{
          padding:"10px 20px", border:"1px solid #e2e8f0", borderRadius:8,
          background:"#fff", cursor:"pointer", fontSize:14, color:"#64748b"
        }}>← Back</button>
        <button onClick={confirm} style={{
          padding:"10px 24px", background:"#1d6fb8", color:"#fff",
          border:"none", borderRadius:8, cursor:"pointer", fontWeight:600, fontSize:14
        }}>
          {selectedCount > 0 ? `Remove ${selectedCount} & Export →` : "Continue to Export →"}
        </button>
        {selectedCount > 0 && (
          <span style={{ color:"#64748b", fontSize:13 }}>
            {statement.transactions.length - selectedCount} transactions remaining
          </span>
        )}
      </div>
    </div>
  )
}
