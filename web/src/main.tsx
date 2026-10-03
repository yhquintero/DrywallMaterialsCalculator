import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { App } from './App';
import { AdminConsole } from './admin/AdminConsole';
import './styles/index.css';

/**
 * Ruteo de la plataforma:
 *   /*        → Calculadora DrywallPro Master (pública)
 *   /admin/*  → Consola profesional de licencias (Roles, Usuarios, Permisos, Keygen)
 */
function Root() {
  return (
    <Routes>
      <Route path="/admin/*" element={<AdminConsole />} />
      <Route path="*" element={<App />} />
    </Routes>
  );
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <Root />
    </BrowserRouter>
  </React.StrictMode>
);
