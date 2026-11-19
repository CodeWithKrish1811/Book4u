import React from 'react';
import { Navbar } from '../components/Navbar';
import { Footer } from '../components/Footer';

export function MainLayout({ children, healthState }) {
  return (
    <div className="layout-wrapper">
      <Navbar healthState={healthState} />
      <main className="layout-main">
        {children}
      </main>
      <Footer />
    </div>
  );
}
