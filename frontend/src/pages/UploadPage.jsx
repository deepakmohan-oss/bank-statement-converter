import { useState, useCallback } from "react"

const C = { navy:"#0d1b2e", gold:"#f0b429", teal:"#0e9f8e", cream:"#f4efe6", grey:"#6b7280", border:"#e5e7eb" }
const BANKS = ["Commonwealth","NAB","Westpac","ANZ","Macquarie","BOQ","Auswide","Bankwest","Bendigo","ING","St George"]

export default function UploadPage({ onDone }) {
  const [drag, setDrag]       = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError]     = useState("")
  const [warns, setWarns]     = useState([])

  const upload = useCallback(async (file) => {
    if (!file) return
    if (!file.name.toLowerCase().endsWith(".pdf")) { setError("Please upload a PDF file."); return }
    if (file.size > 20 * 1024 * 1024) { setError("File too large — maximum 20 MB."); return }
    setLoading(true); setError(""); setWarns([])
    const form = new FormData()
    form.append("file", file)
    try {
      const res  = await fetch("/api/upload", { method:"POST", body:form })
      const data = await res.json()
      if (!data.success) setError(data.errors?.[0] || "Upload failed.")
      else { if (data.warnings?.length) setWarns(data.warnings); onDone(data.statement) }
    } catch { setError("Network error — please try again.") }
    finally { setLoading(false) }
  }, [onDone])

  const onDrop = useCallback(e => {
    e.preventDefault(); setDrag(false); upload(e.dataTransfer.files[0])
  }, [upload])

  return (
    <div style={{ maxWidth:600, margin:"0 auto" }}>

      {/* Hero text matching image 3 */}
      <div style={{ textAlign:"center", marginBottom:40 }}>
        <div style={{
          display:"inline-block", background: C.navy, color:C.gold,
          fontSize:11, fontWeight:700, letterSpacing:"0.1em", textTransform:"uppercase",
          padding:"5px 16px", borderRadius:20, marginBottom:20
        }}>
          OrtúsPro · AU Bank Statement Converter
        </div>
        <h1 style={{ fontSize:36, fontWeight:900, color: C.navy, margin:"0 0 12px", lineHeight:1.15, letterSpacing:"-1px" }}>
          Turn PDF statements<br />
          into clean <span style={{ color: C.teal }}>Excel data</span>
        </h1>
        <p style={{ color: C.grey, fontSize:15, margin:0, lineHeight:1.6 }}>
          Automatically detects bank format — extracts every<br />
          transaction with zero manual work.
        </p>
      </div>

      {/* Drop zone */}
      <div
        onDrop={onDrop}
        onDragOver={e => { e.preventDefault(); setDrag(true) }}
        onDragLeave={() => setDrag(false)}
        onClick={() => !loading && document.getElementById("fi").click()}
        style={{
          border: `2px dashed ${drag ? C.gold : "#c9b99a"}`,
          borderRadius: 16,
          padding: "52px 32px",
          textAlign: "center",
          cursor: loading ? "wait" : "pointer",
          background: drag ? "#fefae8" : "#fff",
          transition: "all 0.2s",
          boxShadow: "0 2px 8px rgba(13,27,46,.07)"
        }}
      >
        <div style={{ fontSize:44, marginBottom:14 }}>{loading ? "⏳" : "📄"}</div>
        <div style={{ fontWeight:800, fontSize:17, color: C.navy, marginBottom:6 }}>
          {loading ? "Detecting bank & extracting transactions…" : "Drop your PDF here"}
        </div>
        <div style={{ color: C.grey, fontSize:14, marginBottom:22 }}>
          {loading ? "This takes 5–15 seconds for large statements" : "or click to browse (max 20 MB)"}
        </div>
        {!loading && (
          <div style={{
            display:"inline-block", background: C.gold, color: C.navy,
            padding:"10px 28px", borderRadius:8, fontWeight:700, fontSize:14
          }}>
            Choose File
          </div>
        )}
        {loading && (
          <div style={{ width:220, height:4, background:"#e5e7eb", borderRadius:4, margin:"0 auto" }}>
            <div style={{ width:"65%", height:"100%", background: C.gold, borderRadius:4,
              animation:"pulse 1.5s ease-in-out infinite" }} />
          </div>
        )}
        <input id="fi" type="file" accept=".pdf" style={{ display:"none" }}
          onChange={e => upload(e.target.files[0])} />
      </div>

      {/* Supported banks */}
      <div style={{ textAlign:"center", marginTop:18 }}>
        <span style={{ color:"#aaa", fontSize:12 }}>Supports: </span>
        <span style={{ color: C.grey, fontSize:12 }}>{BANKS.join(" · ")}</span>
      </div>

      {error && (
        <div style={{ marginTop:20, padding:"14px 18px", background:"#fef2f2",
          border:"1px solid #fca5a5", borderRadius:10, color:"#b91c1c", fontSize:14, lineHeight:1.5 }}>
          <strong>⚠ Error:</strong> {error}
        </div>
      )}
      {warns.map((w, i) => (
        <div key={i} style={{ marginTop:12, padding:"12px 16px", background:"#fffbeb",
          border:"1px solid #fcd34d", borderRadius:10, color:"#92400e", fontSize:13 }}>
          ⚡ {w}
        </div>
      ))}

      <style>{`@keyframes pulse{0%,100%{opacity:1}50%{opacity:.35}}`}</style>
    </div>
  )
}
