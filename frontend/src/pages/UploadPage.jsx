import { useState, useCallback } from "react"

export default function UploadPage({ onStatementParsed }) {
  const [dragging, setDragging] = useState(false)
  const [loading, setLoading]   = useState(false)
  const [error, setError]       = useState("")
  const [warnings, setWarnings] = useState([])

  const upload = useCallback(async (file) => {
    if (!file || !file.name.endsWith(".pdf")) {
      setError("Please upload a PDF file.")
      return
    }
    setLoading(true)
    setError("")
    setWarnings([])

    const form = new FormData()
    form.append("file", file)

    try {
      const res  = await fetch("/api/upload", { method: "POST", body: form })
      const data = await res.json()

      if (!data.success) {
        setError(data.errors?.[0] || "Upload failed.")
      } else {
        if (data.warnings?.length) setWarnings(data.warnings)
        onStatementParsed?.(data)
      }
    } catch (e) {
      setError("Server error. Please try again.")
    } finally {
      setLoading(false)
    }
  }, [onStatementParsed])

  const onDrop = useCallback((e) => {
    e.preventDefault()
    setDragging(false)
    upload(e.dataTransfer.files[0])
  }, [upload])

  return (
    <div style={{ maxWidth: 560, margin: "60px auto", fontFamily: "system-ui, sans-serif" }}>
      <h1 style={{ fontSize: 26, fontWeight: 700, marginBottom: 8 }}>
        AU Bank Statement Converter
      </h1>
      <p style={{ color: "#555", marginBottom: 32 }}>
        Supports Commonwealth, NAB, Westpac, ANZ, Macquarie, BOQ, Bankwest, Bendigo, ING, St George
      </p>

      {/* Drop zone */}
      <div
        onDrop={onDrop}
        onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
        onDragLeave={() => setDragging(false)}
        onClick={() => document.getElementById("file-input").click()}
        style={{
          border: `2px dashed ${dragging ? "#2563eb" : "#cbd5e1"}`,
          borderRadius: 12,
          padding: "48px 32px",
          textAlign: "center",
          cursor: "pointer",
          background: dragging ? "#eff6ff" : "#f8fafc",
          transition: "all 0.2s"
        }}
      >
        <div style={{ fontSize: 40, marginBottom: 12 }}>📄</div>
        <div style={{ fontWeight: 600, marginBottom: 4 }}>
          {loading ? "Parsing..." : "Drop your bank statement PDF here"}
        </div>
        <div style={{ color: "#64748b", fontSize: 14 }}>or click to browse</div>
        <input
          id="file-input"
          type="file"
          accept=".pdf"
          style={{ display: "none" }}
          onChange={(e) => upload(e.target.files[0])}
        />
      </div>

      {loading && (
        <div style={{ marginTop: 20, color: "#2563eb", textAlign: "center" }}>
          ⏳ Detecting bank and extracting transactions…
        </div>
      )}

      {error && (
        <div style={{
          marginTop: 20, padding: "14px 18px",
          background: "#fef2f2", border: "1px solid #fca5a5",
          borderRadius: 8, color: "#b91c1c", fontSize: 14
        }}>
          ⚠ {error}
        </div>
      )}

      {warnings.map((w, i) => (
        <div key={i} style={{
          marginTop: 12, padding: "12px 16px",
          background: "#fffbeb", border: "1px solid #fcd34d",
          borderRadius: 8, color: "#92400e", fontSize: 14
        }}>
          ⚡ {w}
        </div>
      ))}
    </div>
  )
}
