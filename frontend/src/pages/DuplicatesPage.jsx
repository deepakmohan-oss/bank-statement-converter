import { useState, useEffect } from "react"

export default function DuplicatesPage({ statement, onConfirm }) {
  const [duplicates, setDuplicates] = useState([])
  const [checked, setChecked]       = useState({})

  useEffect(() => {
    if (!statement?.transactions) return
    const seen = new Map()
    const dupes = []
    statement.transactions.forEach((t, i) => {
      const key = `${t.date}|${t.description.trim().toUpperCase()}|${t.debit}|${t.credit}`
      if (seen.has(key)) {
        dupes.push({ ...t, index: i })
      } else {
        seen.set(key, i)
      }
    })
    setDuplicates(dupes)
    setChecked(Object.fromEntries(dupes.map((_, i) => [i, true])))
  }, [statement])

  const toggleAll = (val) => setChecked(Object.fromEntries(duplicates.map((_, i) => [i, val])))

  const confirm = () => {
    const removeIndices = new Set(
      duplicates.filter((_, i) => checked[i]).map(d => d.index)
    )
    const cleaned = statement.transactions.filter((_, i) => !removeIndices.has(i))
    onConfirm?.({ ...statement, transactions: cleaned })
  }

  if (!statement) return <div style={{ padding: 40 }}>No statement loaded.</div>

  if (duplicates.length === 0) {
    return (
      <div style={{ padding: 40, fontFamily: "system-ui, sans-serif" }}>
        <h2 style={{ fontSize: 20, fontWeight: 700, marginBottom: 8 }}>Duplicate Check</h2>
        <div style={{ color: "#15803d", fontSize: 15 }}>✓ No duplicate transactions found.</div>
        <button onClick={() => onConfirm?.(statement)} style={{
          marginTop: 20, padding: "10px 24px", background: "#2563eb", color: "#fff",
          border: "none", borderRadius: 8, cursor: "pointer", fontWeight: 600
        }}>
          Continue to Export →
        </button>
      </div>
    )
  }

  const col = { padding: "9px 12px", fontSize: 13 }

  return (
    <div style={{ padding: "32px 24px", fontFamily: "system-ui, sans-serif" }}>
      <h2 style={{ fontSize: 22, fontWeight: 700, marginBottom: 8 }}>Duplicate Transactions</h2>
      <p style={{ color: "#64748b", marginBottom: 20 }}>
        {duplicates.length} duplicate(s) detected. Checked items will be removed.
      </p>

      <div style={{ marginBottom: 12, display: "flex", gap: 12 }}>
        <button onClick={() => toggleAll(true)} style={{ fontSize: 13, cursor: "pointer" }}>
          Select all
        </button>
        <button onClick={() => toggleAll(false)} style={{ fontSize: 13, cursor: "pointer" }}>
          Deselect all
        </button>
      </div>

      <div style={{ overflowX: "auto", borderRadius: 10, border: "1px solid #e2e8f0" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
          <thead>
            <tr style={{ background: "#1e3a5f", color: "#fff" }}>
              <th style={col}>Remove</th>
              <th style={col}>Date</th>
              <th style={col}>Description</th>
              <th style={col}>Debit</th>
              <th style={col}>Credit</th>
              <th style={col}>Balance</th>
            </tr>
          </thead>
          <tbody>
            {duplicates.map((t, i) => (
              <tr key={i} style={{ background: i % 2 === 0 ? "#fff" : "#fef2f2" }}>
                <td style={{ ...col, textAlign: "center" }}>
                  <input type="checkbox" checked={!!checked[i]}
                    onChange={e => setChecked(p => ({ ...p, [i]: e.target.checked }))} />
                </td>
                <td style={col}>{t.date}</td>
                <td style={col}>{t.description}</td>
                <td style={col}>{t.debit?.toFixed(2) ?? ""}</td>
                <td style={col}>{t.credit?.toFixed(2) ?? ""}</td>
                <td style={col}>{t.balance?.toFixed(2) ?? ""}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <button onClick={confirm} style={{
        marginTop: 20, padding: "10px 24px", background: "#2563eb", color: "#fff",
        border: "none", borderRadius: 8, cursor: "pointer", fontWeight: 600
      }}>
        Remove Selected & Continue →
      </button>
    </div>
  )
}
