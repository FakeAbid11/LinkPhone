const DOTTED_QUAD = /^\d{1,3}(\.\d{1,3}){3}$/;

/**
 * Exactly four decimal octets, nothing else.
 *
 * Deliberately strict, because the accepted value is pasted into a PowerShell
 * string. Anything that is not digits and dots has to be refused *before* a
 * command is built, so the check is a whitelist rather than a sanitiser.
 *
 * There is no internal `trim()`: what is validated is exactly what reaches the
 * script, so callers trim and a stray space fails loudly instead of being
 * quietly accepted here and pasted there.
 */
export function isValidIPv4(value: string): boolean {
  if (!DOTTED_QUAD.test(value)) return false;
  const parts = value.split('.');
  for (const part of parts) {
    // `010` is octal to some tools and decimal to others; refusing it keeps the
    // value unambiguous for everything that reads it later.
    if (part.length > 1 && part.startsWith('0')) return false;
    if (Number(part) > 255) return false;
  }
  const first = Number(parts[0]);
  const second = Number(parts[1]);
  // 0/8 means "this host on this network", and subsumes 0.0.0.0.
  if (first === 0) return false;
  if (first === 127) return false;
  // A phone is never on link-local; a probe pointed there only wastes a ping.
  if (first === 169 && second === 254) return false;
  // Multicast, reserved, and the broadcast address.
  if (first >= 224) return false;
  return true;
}

/**
 * True when two addresses share the first three octets.
 *
 * The prefix length is not known to either side of the wire, so this is the most
 * that can be claimed. It is used to decide whether a default gateway is the
 * LAN's router rather than a VPN endpoint, where being approximate is safe: a
 * false negative only makes the copy less specific, never wrong.
 */
export function isSameSlash24(a: string, b: string): boolean {
  const left = a.split('.');
  const right = b.split('.');
  if (left.length !== 4 || right.length !== 4) return false;
  return left[0] === right[0] && left[1] === right[1] && left[2] === right[2];
}
