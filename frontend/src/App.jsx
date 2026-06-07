import { useState } from "react"
import UploadPage     from "./pages/UploadPage"
import ReviewPage     from "./pages/ReviewPage"
import DuplicatesPage from "./pages/DuplicatesPage"
import ExportPage     from "./pages/ExportPage"

const STEPS = ["Upload", "Review", "Duplicates", "Export"]

export default function App() {
  const [step, setStep]           = useState(0)
  const [statement, setStatement] = useState(null)

  const go = (n) => setStep(n)

  const reset = () => { setStatement(null); setStep(0) }

  return (
    <div style={{ minHeight:"100vh", background:"#f0f4f8", fontFamily:"'Inter',system-ui,sans-serif" }}>

      {/* Nav */}
      <header style={{
        background:"#0a1628", borderBottom:"1px solid #1e3a5f",
        padding:"0 24px", display:"flex", alignItems:"center", height:56
      }}>
        <span style={{ color:"#4fa3e0", fontWeight:800, fontSize:18, letterSpacing:"-0.5px", marginRight:40 }}>
          OrtúsPro
        </span>
        <span style={{ color:"#64748b", fontSize:13 }}>AU Bank Statement Converter</span>

        {/* Step indicator */}
        <div style={{ display:"flex", alignItems:"center", gap:0, marginLeft:"auto" }}>
          {STEPS.map((s, i) => (
            <div key={i} style={{ display:"flex", alignItems:"center" }}>
              <button
                onClick={() => i < step && setStep(i)}
                style={{
                  padding:"5px 14px", borderRadius:20, fontSize:12, fontWeight:600,
                  background: i === step ? "#1d6fb8" : "transparent",
                  color: i === step ? "#fff" : i < step ? "#4fa3e0" : "#3d5166",
                  border:"none", cursor: i < step ? "pointer" : "default",
                  transition:"all 0.15s"
                }}
              >
                {i < step ? `✓ ${s}` : s}
              </button>
              {i < STEPS.length - 1 && (
                <span style={{ color:"#1e3a5f", margin:"0 2px", fontSize:16 }}>›</span>
              )}
            </div>
          ))}
        </div>
      </header>

      {/* Content */}
      <main style={{ maxWidth:960, margin:"0 auto", padding:"32px 24px" }}>
        {step === 0 && <UploadPage onDone={s => { setStatement(s); go(1) }} />}
        {step === 1 && <ReviewPage statement={statement} onNext={() => go(2)} onBack={() => go(0)} />}
        {step === 2 && <DuplicatesPage statement={statement}
                          onConfirm={s => { setStatement(s); go(3) }}
                          onBack={() => go(1)} />}
        {step === 3 && <ExportPage statement={statement} onReset={reset} onBack={() => go(2)} />}
      </main>
    </div>
  )
}
