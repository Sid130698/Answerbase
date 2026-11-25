import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { SupportEmbedGate } from './support/SupportEmbedGate'
import { supportMode } from './support/transport'
import { AuthGate } from './auth/AuthGate'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {supportMode ? <SupportEmbedGate><App /></SupportEmbedGate> : <AuthGate><App /></AuthGate>}
  </StrictMode>,
)
