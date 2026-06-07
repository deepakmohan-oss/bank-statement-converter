import { useState, useCallback } from "react"

const BANKS = ["Commonwealth","NAB","Westpac","ANZ","Macquarie","BOQ",
               "Auswide","Bankwest","Bendigo","ING","St George"]

export default function UploadPage({ onDone }) {
  const [drag, setDrag]       = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError]     = useState("")
  const [warns, setWarns]     = useState([])

  const upload = useCallback(async (file) => {
    if (!file) return
    if (!file.name.toLowerCase().endsWith(".pdf")) {
      setError("Please upload a PDF file."); return
    }
    if (file.size > 20 * 1024 * 1024) {
      setError("File too large — maximum 20 MB."); return
    }
    setLoading(true); setError(""); setWarns([])
    const form = new FormData()
    form.append("file", file)
    try {
      const res  = await fetch("/api/upload", { method:"POST", body:form })
      const data = await res.json()
      if (!data.success) { setError(data.errors?.[0] || "Upload failed.") }
      else { if (data.warnings?.length) setWarns(data.warnings); onDone(data.statement) }
    } catch { setError("Network error — please try again.") }
    finally { setLoading(false) }
  }, [onDone])

  const onDrop = useCallback(e => {
    e.preventDefault(); setDrag(false)
    upload(e.dataTransfer.files[0])
  }, [upload])

  return (
    <div style={{ maxWidth:580, margin:"0 auto" }}>
      {/* Hero */}
      <div style={{ textAlign:"center", marginBottom:36 }}>
        <h1 style={{ fontSize:28, fontWeight:800, color:"#0a1628", margin:"0 0 8px" }}>
          Convert Bank Statements
        </h1>
        <p style={{ color:"#64748b", fontSize:15, margin:0 }}>
          Upload any Australian bank statement PDF and export to Excel or CSV instantly.
        </p>
      </div>

      {/* Drop zone */}
      <div
        onDrop={onDrop}
        onDragOver={e => { e.preventDefault(); setDrag(true) }}
        onDragLeave={() => setDrag(false)}
        onClick={() => !loading && document.getElementById("fi").click()}
        style={{
          border:`2px dashed ${drag ? "#1d6fb8" : "#cbd5e1"}`,
          borderRadius:16, padding:"52px 32px", textAlign:"center",
          cursor: loading ? "wait" : "pointer",
          background: drag ? "#eff6ff" : "#fff",
          transition:"all 0.2s",
          boxShadow:"0 1px 3px rgba(0,0,0,.06)"
        }}
      >
        <div style={{ fontSize:48, marginBottom:14 }}>
          {loading ? "⏳" : "📄"}
        </div>
        <div style={{ fontWeight:700, fontSize:16, color:"#0a1628", marginBottom:6 }}>
          {loading ? "Parsing statement…" : "Drop your PDF here"}
        </div>
        <div style={{ color:"#64748b", fontSize:14, marginBottom:20 }}>
          {loading ? "Detecting bank and extracting transactions" : "or click to browse (max 20 MB)"}
        </div>
        {!loading && (
          <div style={{
            display:"inline-block", background:"#1d6fb8", color:"#fff",
            padding:"9px 22px", borderRadius:8, fontWeight:600, fontSize:14
          }}>
            Choose File
          </div>
        )}
        {loading && (
          <div style={{
            width:200, height:4, background:"#e2e8f0", borderRadius:4, margin:"0 auto"
          }}>
            <div style={{
              width:"60%", height:"100%", background:"#1d6fb8",
              borderRadius:4, animation:"pulse 1.5s ease-in-out infinite"
            }} />
          </div>
        )}
        <input id="fi" type="file" accept=".pdf" style={{ display:"none" }}
          onChange={e => upload(e.target.files[0])} />
      </div>

      {/* Supported banks */}
      <div style={{ marginTop:20, textAlign:"center" }}>
        <span style={{ color:"#94a3b8", fontSize:12 }}>Supports: </span>
        <span style={{ color:"#64748b", fontSize:12 }}>{BANKS.join(" · ")}</span>
      </div>

      {/* Error */}
      {error && (
        <div style={{
          marginTop:20, padding:"14px 18px",
          background:"#fef2f2", border:"1px solid #fca5a5",
          borderRadius:10, color:"#b91c1c", fontSize:14, lineHeight:1.5
        }}>
          <strong>⚠ Error:</strong> {error}
        </div>
      )}

      {/* Warnings */}
      {warns.map((w, i) => (
        <div key={i} style={{
          marginTop:12, padding:"12px 16px",
          background:"#fffbeb", border:"1px solid #fcd34d",
          borderRadius:10, color:"#92400e", fontSize:13
        }}>
          ⚡ {w}
        </div>
      ))}

      <style>{`@keyframes pulse{0%,100%{opacity:1}50%{opacity:.4}}`}</style>
    </div>
  )
}
