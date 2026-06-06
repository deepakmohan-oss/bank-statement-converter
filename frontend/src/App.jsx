import { useState } from "react"
import UploadPage     from "./pages/UploadPage"
import ReviewPage     from "./pages/ReviewPage"
import DuplicatesPage from "./pages/DuplicatesPage"
import ExportPage     from "./pages/ExportPage"

const STEPS = ["Upload", "Review", "Duplicates", "Export"]

export default function App() {
  const [step, setStep]           = useState(0)
  const [statement, setStatement] = useState(null)

  const handleUploaded = (data) => {
    setStatement(data.statement)
    setStep(1)
  }

  const handleReviewNext = () => setStep(2)

  const handleDuplicatesConfirm = (cleaned) => {
    setStatement(cleaned)
    setStep(3)
  }

  const startOver = () => {
    setStatement(null)
    setStep(0)
  }

  return (
    <div style={{ minHeight: "100vh", background: "#f8fafc" }}>
      {/* Step indicator */}
      <div style={{
        background: "#1e3a5f", padding: "12px 24px",
        display: "flex", gap: 0, alignItems: "center"
      }}>
        <span style={{ color: "#93c5fd", fontWeight: 700, fontSize: 15, marginRight: 32 }}>
          OrtúsPro
        </span>
        {STEPS.map((s, i) => (
          <div key={i} style={{ display: "flex", alignItems: "center" }}>
            <div style={{
              padding: "4px 14px", borderRadius: 20, fontSize: 13, fontWeight: 600,
              background: i === step ? "#2563eb" : "transparent",
              color: i === step ? "#fff" : i < step ? "#93c5fd" : "#475569",
              cursor: i < step ? "pointer" : "default"
            }} onClick={() => i < step && setStep(i)}>
              {i < step ? "✓ " : ""}{s}
            </div>
            {i < STEPS.length - 1 && (
              <span style={{ color: "#334155", margin: "0 4px" }}>›</span>
            )}
          </div>
        ))}
      </div>

      {/* Page content */}
      <div style={{ maxWidth: 900, margin: "0 auto" }}>
        {step === 0 && <UploadPage     onStatementParsed={handleUploaded} />}
        {step === 1 && <ReviewPage     statement={statement} onNext={handleReviewNext} />}
        {step === 2 && <DuplicatesPage statement={statement} onConfirm={handleDuplicatesConfirm} />}
        {step === 3 && <ExportPage     statement={statement} onStartOver={startOver} />}
      </div>
    </div>
  )
}
