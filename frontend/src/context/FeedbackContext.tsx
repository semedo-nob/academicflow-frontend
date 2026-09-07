import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { Button } from '../components/ui/Button';
import { IconClose } from '../components/ui/Icons';

export type ToastVariant = 'success' | 'error' | 'info' | 'warning';

type ToastItem = {
  id: number;
  message: string;
  variant: ToastVariant;
};

type ConfirmOptions = {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  danger?: boolean;
};

type PromptOptions = {
  title: string;
  message: string;
  placeholder?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  required?: boolean;
};

type FeedbackContextValue = {
  toast: (message: string, variant?: ToastVariant) => void;
  success: (message: string) => void;
  error: (message: string) => void;
  confirm: (options: ConfirmOptions) => Promise<boolean>;
  prompt: (options: PromptOptions) => Promise<string | null>;
};

const FeedbackContext = createContext<FeedbackContextValue | null>(null);

export function FeedbackProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const toastId = useRef(0);

  const [confirmState, setConfirmState] = useState<(ConfirmOptions & { open: boolean }) | null>(null);
  const confirmResolver = useRef<((value: boolean) => void) | null>(null);

  const [promptState, setPromptState] = useState<(PromptOptions & { open: boolean; value: string }) | null>(
    null,
  );
  const promptResolver = useRef<((value: string | null) => void) | null>(null);

  const dismissToast = useCallback((id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const toast = useCallback(
    (message: string, variant: ToastVariant = 'info') => {
      const id = ++toastId.current;
      setToasts((prev) => [...prev.slice(-4), { id, message, variant }]);
      window.setTimeout(() => dismissToast(id), 4500);
    },
    [dismissToast],
  );

  const success = useCallback((message: string) => toast(message, 'success'), [toast]);
  const error = useCallback((message: string) => toast(message, 'error'), [toast]);

  const confirm = useCallback((options: ConfirmOptions) => {
    return new Promise<boolean>((resolve) => {
      confirmResolver.current = resolve;
      setConfirmState({ ...options, open: true });
    });
  }, []);

  const prompt = useCallback((options: PromptOptions) => {
    return new Promise<string | null>((resolve) => {
      promptResolver.current = resolve;
      setPromptState({ ...options, open: true, value: '' });
    });
  }, []);

  const closeConfirm = (result: boolean) => {
    confirmResolver.current?.(result);
    confirmResolver.current = null;
    setConfirmState(null);
  };

  const closePrompt = (result: string | null) => {
    promptResolver.current?.(result);
    promptResolver.current = null;
    setPromptState(null);
  };

  const value = useMemo(
    () => ({ toast, success, error, confirm, prompt }),
    [toast, success, error, confirm, prompt],
  );

  return (
    <FeedbackContext.Provider value={value}>
      {children}

      <div className="snackbar-host" aria-live="polite" aria-relevant="additions">
        {toasts.map((t) => (
          <div key={t.id} className={`snackbar snackbar-${t.variant}`} role="status">
            <span className="snackbar-msg">{t.message}</span>
            <button type="button" className="snackbar-close" onClick={() => dismissToast(t.id)} aria-label="Dismiss">
              <IconClose />
            </button>
          </div>
        ))}
      </div>

      {confirmState?.open ? (
        <>
          <div className="overlay active" onClick={() => closeConfirm(false)} />
          <div className="site-dialog" role="alertdialog" aria-modal="true" aria-labelledby="confirm-title">
            <div className="site-dialog-head">
              <div id="confirm-title" className="site-dialog-title">
                {confirmState.title}
              </div>
              <button type="button" className="drawer-close" onClick={() => closeConfirm(false)} aria-label="Close">
                <IconClose />
              </button>
            </div>
            <div className="site-dialog-body">{confirmState.message}</div>
            <div className="site-dialog-foot">
              <Button onClick={() => closeConfirm(false)}>{confirmState.cancelLabel || 'Cancel'}</Button>
              <Button
                variant={confirmState.danger ? 'danger' : 'primary'}
                style={{ marginLeft: 'auto' }}
                onClick={() => closeConfirm(true)}
              >
                {confirmState.confirmLabel || 'Confirm'}
              </Button>
            </div>
          </div>
        </>
      ) : null}

      {promptState?.open ? (
        <>
          <div className="overlay active" onClick={() => closePrompt(null)} />
          <div className="site-dialog" role="dialog" aria-modal="true" aria-labelledby="prompt-title">
            <div className="site-dialog-head">
              <div id="prompt-title" className="site-dialog-title">
                {promptState.title}
              </div>
              <button type="button" className="drawer-close" onClick={() => closePrompt(null)} aria-label="Close">
                <IconClose />
              </button>
            </div>
            <div className="site-dialog-body">
              <p className="section-sub" style={{ marginBottom: 12 }}>
                {promptState.message}
              </p>
              <div className="field">
                <input
                  autoFocus
                  value={promptState.value}
                  placeholder={promptState.placeholder || ''}
                  onChange={(e) => setPromptState((s) => (s ? { ...s, value: e.target.value } : s))}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      const v = promptState.value.trim();
                      if (promptState.required !== false && !v) return;
                      closePrompt(v || null);
                    }
                  }}
                />
              </div>
            </div>
            <div className="site-dialog-foot">
              <Button onClick={() => closePrompt(null)}>{promptState.cancelLabel || 'Cancel'}</Button>
              <Button
                variant="primary"
                style={{ marginLeft: 'auto' }}
                onClick={() => {
                  const v = promptState.value.trim();
                  if (promptState.required !== false && !v) return;
                  closePrompt(v || null);
                }}
              >
                {promptState.confirmLabel || 'Continue'}
              </Button>
            </div>
          </div>
        </>
      ) : null}
    </FeedbackContext.Provider>
  );
}

export function useFeedback() {
  const ctx = useContext(FeedbackContext);
  if (!ctx) throw new Error('useFeedback must be used within FeedbackProvider');
  return ctx;
}
