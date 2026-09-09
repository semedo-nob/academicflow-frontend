import { ClerkProvider, useAuth } from '@clerk/react';
import { StrictMode, useEffect, type ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import { clerkConfigured, clerkPublishableKey } from './lib/clerk';
import { setClerkTokenGetter } from './services/api';
import './styles/app.css';

function ClerkTokenBridge({ children }: { children: ReactNode }) {
  const { getToken, isLoaded } = useAuth();
  useEffect(() => {
    if (!isLoaded) return;
    setClerkTokenGetter(async () => {
      try {
        return (await getToken()) || null;
      } catch {
        return null;
      }
    });
    return () => setClerkTokenGetter(null);
  }, [getToken, isLoaded]);
  return <>{children}</>;
}

const key = clerkPublishableKey();
const tree = clerkConfigured && key ? (
  <ClerkProvider publishableKey={key} afterSignOutUrl="/">
    <ClerkTokenBridge>
      <App />
    </ClerkTokenBridge>
  </ClerkProvider>
) : (
  <App />
);

createRoot(document.getElementById('root')!).render(<StrictMode>{tree}</StrictMode>);
