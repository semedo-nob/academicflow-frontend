import { useAuth } from '@clerk/react';
import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import { useApp } from '../../context/AppContext';
import { clerkConfigured } from '../../lib/clerk';
import { INVITE_TOKEN_STORAGE_KEY } from '../../lib/inviteContext';

/**
 * Bridges Clerk sign-in to AcademicFlow session when not in an invitation flow.
 * Invitation acceptance owns session establishment after email match.
 */
export function ClerkSessionSync() {
  if (!clerkConfigured) return null;
  return <ClerkSessionSyncInner />;
}

function ClerkSessionSyncInner() {
  const { isLoaded, isSignedIn } = useAuth();
  const { authenticated, establishClerkSession, clerkEnabled, authLoading } = useApp();
  const location = useLocation();
  const inflight = useRef(false);
  const lastAttempt = useRef(0);

  useEffect(() => {
    if (!clerkEnabled || authLoading || !isLoaded || !isSignedIn || authenticated) return;
    if (location.pathname === '/login' || location.pathname.startsWith('/invite/')) return;
    const params = new URLSearchParams(location.search);
    const inviteInUrl = params.get('invite')?.trim();
    const inviteStored = sessionStorage.getItem(INVITE_TOKEN_STORAGE_KEY)?.trim();
    if (inviteInUrl || inviteStored) return;
    if (inflight.current) return;
    const now = Date.now();
    if (now - lastAttempt.current < 2000) return;
    lastAttempt.current = now;
    inflight.current = true;
    void establishClerkSession()
      .catch(() => {
        /* Login page / invite flow surfaces the error */
      })
      .finally(() => {
        inflight.current = false;
      });
  }, [
    clerkEnabled,
    authLoading,
    isLoaded,
    isSignedIn,
    authenticated,
    establishClerkSession,
    location.pathname,
    location.search,
  ]);

  return null;
}
