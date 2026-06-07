import { useState } from "react"
import UploadPage     from "./pages/UploadPage"
import ReviewPage     from "./pages/ReviewPage"
import DuplicatesPage from "./pages/DuplicatesPage"
import ExportPage     from "./pages/ExportPage"

const STEPS = ["Upload", "Review", "Duplicates", "Export"]

// OrtúsPro brand colours
const C = {
  navy:   "#0d1b2e",
  navyL:  "#162840",
  gold:   "#f0b429",
  teal:   "#0e9f8e",
  cream:  "#f4efe6",
  text:   "#111827",
  grey:   "#6b7280",
  border: "#e5e7eb",
}

export default function App() {
  const [step, setStep]           = useState(0)
  const [statement, setStatement] = useState(null)

  const reset = () => { setStatement(null); setStep(0) }

  return (
    <div style={{ minHeight:"100vh", background: C.cream, fontFamily:"'Inter',system-ui,sans-serif", color: C.text }}>

      {/* ── Header ─────────────────────────────────────────────────────── */}
      <header style={{
        background: C.navy,
        borderBottom: `3px solid ${C.gold}`,
        padding: "0 32px",
        display: "flex",
        alignItems: "center",
        height: 60,
        gap: 32
      }}>
        {/* Logo */}
        <div style={{ display:"flex", alignItems:"center", gap:10, flexShrink:0 }}>
          {/* SVG icon matching the OrtúsPro circular logo */}
          <svg width="34" height="34" viewBox="0 0 34 34" fill="none">
            <circle cx="17" cy="17" r="16" stroke={C.gold} strokeWidth="2.5" fill="none"/>
            <path d="M17 3 A14 14 0 0 1 31 17" stroke={C.gold} strokeWidth="3" strokeLinecap="round" fill="none"/>
            <text x="17" y="22" textAnchor="middle" fill="#fff"
              fontSize="13" fontWeight="800" fontFamily="Inter,sans-serif">OP</text>
          </svg>
          <span style={{ color:"#fff", fontWeight:800, fontSize:17, letterSpacing:"-0.3px" }}>
            Ortús<span style={{ color: C.gold }}>Pro</span>
            <span style={{ color:"#8ca0bc", fontWeight:400, fontSize:13, marginLeft:6 }}>Global</span>
          </span>
        </div>

        <div style={{ width:1, height:28, background:"#1e3a5f" }} />

        <span style={{ color:"#8ca0bc", fontSize:12, fontWeight:500, letterSpacing:"0.06em", textTransform:"uppercase" }}>
          AU Bank Statement Converter
        </span>

        {/* Step indicator */}
        <div style={{ display:"flex", alignItems:"center", marginLeft:"auto", gap:4 }}>
          {STEPS.map((s, i) => (
            <div key={i} style={{ display:"flex", alignItems:"center" }}>
              <button onClick={() => i < step && setStep(i)} style={{
                padding:"4px 14px",
                borderRadius:20,
                fontSize:12,
                fontWeight:600,
                border:"none",
                cursor: i < step ? "pointer" : "default",
                background: i === step ? C.gold : "transparent",
                color: i === step ? C.navy : i < step ? C.gold : "#3d5166",
                transition:"all 0.15s"
              }}>
                {i < step ? `✓ ${s}` : s}
              </button>
              {i < STEPS.length - 1 && (
                <span style={{ color:"#1e3a5f", fontSize:16, margin:"0 2px" }}>›</span>
              )}
            </div>
          ))}
        </div>
      </header>

      {/* ── Content ────────────────────────────────────────────────────── */}
      <main style={{ maxWidth:980, margin:"0 auto", padding:"36px 24px" }}>
        {step === 0 && <UploadPage onDone={s => { setStatement(s); setStep(1) }} />}
        {step === 1 && <ReviewPage statement={statement} onNext={() => setStep(2)} onBack={() => setStep(0)} />}
        {step === 2 && <DuplicatesPage statement={statement}
                          onConfirm={s => { setStatement(s); setStep(3) }}
                          onBack={() => setStep(1)} />}
        {step === 3 && <ExportPage statement={statement} onReset={reset} onBack={() => setStep(2)} />}
      </main>
    </div>
  )
}
