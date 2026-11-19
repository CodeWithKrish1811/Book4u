import React, { createContext, useContext, useState } from 'react';

const AppContext = createContext();

export function AppProvider({ children }) {
  const [campus, setCampus] = useState('Main Campus');
  const [activeTheme, setActiveTheme] = useState('light');

  const value = {
    campus,
    setCampus,
    activeTheme,
    setActiveTheme,
  };

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useApp() {
  const context = useContext(AppContext);
  if (!context) {
    throw new Error('useApp must be used within an AppProvider');
  }
  return context;
}
