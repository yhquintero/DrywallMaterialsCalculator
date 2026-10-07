import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { App } from './App';
import { AdminConsole } from './admin/AdminConsole';
import { RemoteAssistPage } from './components/RemoteAssistPage';
import './styles/index.css';

/**
 * Ruteo de la plataforma:
 *   /*            → Calculadora DrywallPro Master (pública)
 *   /asistencia   → Sala de asistencia remota por WebRTC (técnico en obra)
 *   /admin/*      → Consola profesional de licencias (Roles, Usuarios, Permisos, Keygen)
 */
export function Root() {
  return (
    <Routes>
      <Route path="/admin/*" element={<AdminConsole />} />
      <Route path="/asistencia" element={<RemoteAssistPage />} />
      <Route path="*" element={<App />} />
    </Routes>
  );
}

const rootElement = typeof document !== 'undefined' ? document.getElementById('root') : null;
if (rootElement) {
  ReactDOM.createRoot(rootElement).render(
    <React.StrictMode>
      <BrowserRouter>
        <Root />
      </BrowserRouter>
    </React.StrictMode>
  );
}
