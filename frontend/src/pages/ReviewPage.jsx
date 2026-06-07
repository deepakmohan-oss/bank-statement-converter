import { useState, useMemo } from "react"

const COL = { padding:"10px 14px", fontSize:13 }
const HDR = { ...COL, background:"#0a1628", color:"#fff", cursor:"pointer",
              userSelect:"none", whiteSpace:"nowrap", fontWeight:600 }

export default function ReviewPage({ statement, onNext, onBack }) {
  const [search, setSearch]   = useState("")
  const [sortCol, setSortCol] = useState("date")
  const [sortDir, setSortDir] = useState("asc")
  const [page, setPage]       = useState(1)
  const PAGE_SIZE = 50

  const txns = statement?.transactions ?? []

  const filtered = useMemo(() => {
    let rows = txns
    if (search.trim()) {
      const q = search.toLowerCase()
      rows = rows.filter(t =>
        t.date.includes(q) || t.description.toLowerCase().includes(q) ||
        String(t.debit ?? "").includes(q) || String(t.credit ?? "").includes(q)
      )
    }
    return [...rows].sort((a, b) => {
      const va = a[sortCol] ?? 0
      const vb = b[sortCol] ?? 0
      const cmp = typeof va === "number"
        ? va - vb
        : String(va).localeCompare(String(vb))
      return sortDir === "asc" ? cmp : -cmp
    })
  }, [txns, search, sortCol, sortDir])

  const pages     = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const pageRows  = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)
  const totalD    = txns.reduce((s, t) => s + (t.debit  ?? 0), 0)
  const totalC    = txns.reduce((s, t) => s + (t.credit ?? 0), 0)
  const fmt       = v => v == null ? "" : `$${v.toFixed(2)}`

  const sort = col => {
    if (sortCol === col) setSortDir(d => d === "asc" ? "desc" : "asc")
    else { setSortCol(col); setSortDir("asc") }
    setPage(1)
  }
  const arrow = col => sortCol === col ? (sortDir === "asc" ? " ↑" : " ↓") : ""

  if (!statement) return null

  return (
    <div>
      {/* Summary bar */}
      <div style={{
        background:"#fff", borderRadius:12, padding:"18px 24px",
        marginBottom:20, display:"flex", gap:40, alignItems:"center",
        boxShadow:"0 1px 3px rgba(0,0,0,.06)", flexWrap:"wrap"
      }}>
        <div>
          <div style={{ color:"#64748b", fontSize:12, fontWeight:600 }}>BANK</div>
          <div style={{ fontWeight:700, fontSize:15 }}>{statement.bank}</div>
        </div>
        <div>
          <div style={{ color:"#64748b", fontSize:12, fontWeight:600 }}>ACCOUNT</div>
          <div style={{ fontWeight:700, fontSize:15 }}>{statement.accountNumber || "—"}</div>
        </div>
        <div>
          <div style={{ color:"#64748b", fontSize:12, fontWeight:600 }}>TRANSACTIONS</div>
          <div style={{ fontWeight:700, fontSize:15 }}>{txns.length}</div>
        </div>
        <div>
          <div style={{ color:"#64748b", fontSize:12, fontWeight:600 }}>TOTAL DEBITS</div>
          <div style={{ fontWeight:700, fontSize:15, color:"#b91c1c" }}>{fmt(totalD)}</div>
        </div>
        <div>
          <div style={{ color:"#64748b", fontSize:12, fontWeight:600 }}>TOTAL CREDITS</div>
          <div style={{ fontWeight:700, fontSize:15, color:"#15803d" }}>{fmt(totalC)}</div>
        </div>
        {statement.closingBalance != null && (
          <div>
            <div style={{ color:"#64748b", fontSize:12, fontWeight:600 }}>CLOSING BALANCE</div>
            <div style={{ fontWeight:700, fontSize:15 }}>{fmt(statement.closingBalance)}</div>
          </div>
        )}
      </div>

      {/* Search */}
      <div style={{ position:"relative", marginBottom:16 }}>
        <span style={{ position:"absolute", left:14, top:"50%", transform:"translateY(-50%)",
          color:"#94a3b8", pointerEvents:"none" }}>🔍</span>
        <input
          placeholder="Search transactions…"
          value={search}
          onChange={e => { setSearch(e.target.value); setPage(1) }}
          style={{
            width:"100%", padding:"10px 14px 10px 40px",
            border:"1px solid #e2e8f0", borderRadius:8, fontSize:14,
            boxSizing:"border-box", background:"#fff",
            boxShadow:"0 1px 2px rgba(0,0,0,.04)"
          }}
        />
        {search && (
          <button onClick={() => { setSearch(""); setPage(1) }}
            style={{ position:"absolute", right:12, top:"50%", transform:"translateY(-50%)",
              border:"none", background:"none", cursor:"pointer", color:"#94a3b8", fontSize:18 }}>
            ×
          </button>
        )}
      </div>

      {/* Table */}
      <div style={{ background:"#fff", borderRadius:12, overflow:"hidden",
        boxShadow:"0 1px 3px rgba(0,0,0,.06)", marginBottom:20 }}>
        <div style={{ overflowX:"auto" }}>
          <table style={{ width:"100%", borderCollapse:"collapse", fontSize:13 }}>
            <thead>
              <tr>
                {[["date","Date"],["description","Description"],
                  ["debit","Debit"],["credit","Credit"],["balance","Balance"]].map(([k,l]) => (
                  <th key={k} style={{ ...HDR, textAlign: k === "description" ? "left" : "right",
                    ...(k === "date" && { textAlign:"left" }) }}
                    onClick={() => sort(k)}>
                    {l}{arrow(k)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {pageRows.map((t, i) => (
                <tr key={i} style={{ background: i % 2 === 0 ? "#fff" : "#f8fafc",
                  borderBottom:"1px solid #f1f5f9" }}>
                  <td style={{ ...COL, fontVariantNumeric:"tabular-nums", whiteSpace:"nowrap" }}>
                    {t.date}
                  </td>
                  <td style={{ ...COL, maxWidth:320, overflow:"hidden",
                    textOverflow:"ellipsis", whiteSpace:"nowrap" }} title={t.description}>
                    {t.description}
                  </td>
                  <td style={{ ...COL, textAlign:"right", color:"#b91c1c",
                    fontVariantNumeric:"tabular-nums" }}>
                    {t.debit != null ? t.debit.toFixed(2) : ""}
                  </td>
                  <td style={{ ...COL, textAlign:"right", color:"#15803d",
                    fontVariantNumeric:"tabular-nums" }}>
                    {t.credit != null ? t.credit.toFixed(2) : ""}
                  </td>
                  <td style={{ ...COL, textAlign:"right", fontWeight:500,
                    fontVariantNumeric:"tabular-nums" }}>
                    {t.balance != null ? t.balance.toFixed(2) : ""}
                  </td>
                </tr>
              ))}
              {pageRows.length === 0 && (
                <tr><td colSpan={5} style={{ ...COL, textAlign:"center", color:"#94a3b8", padding:32 }}>
                  No transactions match your search.
                </td></tr>
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {pages > 1 && (
          <div style={{ padding:"12px 16px", borderTop:"1px solid #f1f5f9",
            display:"flex", alignItems:"center", gap:8, fontSize:13, color:"#64748b" }}>
            <span>Page {page} of {pages} · {filtered.length} results</span>
            <div style={{ marginLeft:"auto", display:"flex", gap:6 }}>
              {[["←", page > 1, () => setPage(p => p-1)],
                ["→", page < pages, () => setPage(p => p+1)]].map(([lbl, en, fn], i) => (
                <button key={i} onClick={fn} disabled={!en} style={{
                  padding:"4px 12px", borderRadius:6, border:"1px solid #e2e8f0",
                  background: en ? "#fff" : "#f8fafc", cursor: en ? "pointer" : "default",
                  color: en ? "#0a1628" : "#cbd5e1"
                }}>{lbl}</button>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Actions */}
      <div style={{ display:"flex", gap:12 }}>
        <button onClick={onBack} style={{
          padding:"10px 20px", border:"1px solid #e2e8f0", borderRadius:8,
          background:"#fff", cursor:"pointer", fontSize:14, color:"#64748b"
        }}>← Back</button>
        <button onClick={onNext} style={{
          padding:"10px 24px", background:"#1d6fb8", color:"#fff",
          border:"none", borderRadius:8, cursor:"pointer", fontWeight:600, fontSize:14
        }}>Check Duplicates →</button>
      </div>
    </div>
  )
}
