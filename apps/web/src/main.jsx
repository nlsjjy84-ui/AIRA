import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.jsx'
import CanonicalExplorer from './CanonicalExplorer.jsx'
import './styles.css'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    {window.location.pathname === '/explore' ? <CanonicalExplorer /> : <App />}
  </StrictMode>,
)
