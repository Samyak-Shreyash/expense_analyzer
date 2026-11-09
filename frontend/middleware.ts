// Vercel Routing Middleware: proxies /api/* to the backend (Cloudflare Tunnel) so the browser
// keeps one origin. Adds the caller's IP plus a shared secret, which the backend's
// TrustedProxyClientIpFilter needs before it trusts that IP for rate limiting.
// Vercel env vars: API_ORIGIN (e.g. https://api.example.com), PROXY_SHARED_SECRET.

export const config = { matcher: '/api/:path*' }

export default function middleware(request: Request): Promise<Response> {
  const url = new URL(request.url)
  const target = new URL(url.pathname + url.search, process.env.API_ORIGIN)

  const headers = new Headers(request.headers)
  headers.set('X-Proxy-Secret', process.env.PROXY_SHARED_SECRET ?? '')
  // Vercel sets x-real-ip from the TCP connection; a client can't override it.
  headers.set('X-Client-IP', request.headers.get('x-real-ip') ?? '')

  return fetch(target, {
    method: request.method,
    headers,
    body: request.body,
    redirect: 'manual',
    // @ts-expect-error -- required by the fetch spec to stream a request body
    duplex: 'half',
  })
}
