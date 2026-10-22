export function readCookie(cookieString: string, name: string): string | null {
  for (const pair of cookieString.split("; ")) {
    const separator = pair.indexOf("=");
    if (separator !== -1 && pair.slice(0, separator) === name) {
      return pair.slice(separator + 1) || null;
    }
  }
  return null;
}
