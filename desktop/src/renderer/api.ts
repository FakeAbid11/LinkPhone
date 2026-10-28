import type { LinkPhoneApi } from '../shared/types';

declare global {
  interface Window {
    linkphone: LinkPhoneApi;
  }
}

export const api: LinkPhoneApi = window.linkphone;
