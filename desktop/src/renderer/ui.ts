export interface Ui {
  toast: (kind: 'info' | 'error' | 'success', message: string) => void;
  run: (action: () => Promise<unknown>, success?: string) => Promise<void>;
  refresh: () => Promise<void>;
}
