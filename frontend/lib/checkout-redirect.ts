export function redirectToCheckoutUrl(checkoutUrl: string) {
  globalThis.window.location.assign(checkoutUrl);
}
