import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AppProvider } from './context/AppContext';
import { MainLayout } from './layouts/MainLayout';
import { HomePage } from './pages/HomePage';
import { NotFoundPage } from './pages/NotFoundPage';
import { useHealthCheck } from './hooks/useHealthCheck';
import './styles/index.css';
import './styles/home.css';

export function AppContent() {
  // Check health on load, auto-check can be triggered by user refresh
  const healthState = useHealthCheck();

  return (
    <MainLayout healthState={healthState}>
      <Routes>
        <Route path="/" element={<HomePage healthState={healthState} />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </MainLayout>
  );
}

export default function App() {
  return (
    <AppProvider>
      <BrowserRouter>
        <AppContent />
      </BrowserRouter>
    </AppProvider>
  );
}
