import { useState, useMemo } from "react"

export default function ReviewPage({ statement, onNext }) {
  const [search, setSearch]   = useState("")
  const [sortCol, setSortCol] = useState("date")
  const [sortDir, setSortDir] = useState("asc")

  const txns = statement?.transactions ?? []

  const filtered = useMemo(() => {
    let rows = txns
    if (search) {
      const q = search.toLowerCase()
      rows = rows.filter(t =>
        t.date.includes(q) ||
        t.description.toLowerCase().includes(q) ||
        String(t.debit ?? "").includes(q) ||
        String(t.credit ?? "").includes(q)
      )
    }
    return [...rows].sort((a, b) => {
      const va = a[sortCol] ?? ""
      const vb = b[sortCol] ?? ""
      const cmp = String(va).localeCompare(String(vb), undefined, { numeric: true })
      return sortDir === "asc" ? cmp : -cmp
    })
  }, [txns, search, sortCol, sortDir])

  const toggleSort = (col) => {
    if (sortCol === col) setSortDir(d => d === "asc" ? "desc" : "asc")
    else { setSortCol(col); setSortDir("asc") }
  }

  const totalDebits  = txns.reduce((s, t) => s + (t.debit  ?? 0), 0)
  const totalCredits = txns.reduce((s, t) => s + (t.credit ?? 0), 0)

  if (!statement) return <div style={{ padding: 40 }}>No statement loaded.</div>

  const col = { padding: "10px 14px", textAlign: "left", fontSize: 13, whiteSpace: "nowrap" }
  const hdr = { ...col, background: "#1e3a5f", color: "#fff", cursor: "pointer", userSelect: "none" }

  return (
    <div style={{ padding: "32px 24px", fontFamily: "system-ui, sans-serif" }}>
      {/* Header */}
      <div style={{ marginBottom: 24 }}>
        <h2 style={{ fontSize: 22, fontWeight: 700, marginBottom: 4 }}>
          {statement.bank} — {statement.accountNumber}
        </h2>
        <div style={{ color: "#64748b", fontSize: 14 }}>
          {statement.statementFrom && `${statement.statementFrom} → ${statement.statementTo} · `}
          {txns.length} transactions · Debits ${totalDebits.toFixed(2)} · Credits ${totalCredits.toFixed(2)}
        </div>
      </div>

      {/* Search */}
      <input
        placeholder="Search transactions…"
        value={search}
        onChange={e => setSearch(e.target.value)}
        style={{
          width: "100%", padding: "10px 14px", marginBottom: 16,
          border: "1px solid #cbd5e1", borderRadius: 8, fontSize: 14, boxSizing: "border-box"
        }}
      />

      {/* Table */}
      <div style={{ overflowX: "auto", borderRadius: 10, border: "1px solid #e2e8f0" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
          <thead>
            <tr>
              {["date","description","debit","credit","balance"].map(c => (
                <th key={c} style={hdr} onClick={() => toggleSort(c)}>
                  {c.charAt(0).toUpperCase() + c.slice(1)}
                  {sortCol === c ? (sortDir === "asc" ? " ↑" : " ↓") : ""}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {filtered.map((t, i) => (
              <tr key={i} style={{ background: i % 2 === 0 ? "#fff" : "#f8fafc" }}>
                <td style={col}>{t.date}</td>
                <td style={{ ...col, maxWidth: 320, overflow: "hidden", textOverflow: "ellipsis" }}>{t.description}</td>
                <td style={{ ...col, color: t.debit ? "#b91c1c" : undefined, textAlign: "right" }}>
                  {t.debit != null ? t.debit.toFixed(2) : ""}
                </td>
                <td style={{ ...col, color: t.credit ? "#15803d" : undefined, textAlign: "right" }}>
                  {t.credit != null ? t.credit.toFixed(2) : ""}
                </td>
                <td style={{ ...col, textAlign: "right" }}>
                  {t.balance != null ? t.balance.toFixed(2) : ""}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Next */}
      <div style={{ marginTop: 20, display: "flex", gap: 12 }}>
        <button onClick={onNext} style={{
          padding: "10px 24px", background: "#2563eb", color: "#fff",
          border: "none", borderRadius: 8, cursor: "pointer", fontWeight: 600
        }}>
          Continue to Export →
        </button>
      </div>
    </div>
  )
}
