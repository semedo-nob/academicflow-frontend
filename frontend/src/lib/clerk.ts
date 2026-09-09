const publishableKey = import.meta.env.VITE_CLERK_PUBLISHABLE_KEY as string | undefined;

/** True when a real Clerk publishable key is configured for the SPA. */
export const clerkConfigured = Boolean(publishableKey?.startsWith('pk_'));

export function clerkPublishableKey(): string | undefined {
  return clerkConfigured ? publishableKey : undefined;
}
