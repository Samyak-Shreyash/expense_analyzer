// Vercel Routing Middleware: proxies /api/* to the backend (Cloudflare Tunnel) so the browser
// keeps one origin. Adds the caller's IP plus a shared secret, which the backend's
// TrustedProxyClientIpFilter needs before it trusts that IP for rate limiting.
// Vercel env vars: API_ORIGIN (e.g. https://api.example.com), PROXY_SHARED_SECRET.

export const config = { matcher: '/api/:path*' }

export default async function middleware(request: Request): Promise<Response> {
  const url = new URL(request.url)
  const target = new URL(url.pathname + url.search, process.env.API_ORIGIN)

  const headers = new Headers(request.headers)
  headers.set('X-Proxy-Secret', process.env.PROXY_SHARED_SECRET ?? '')
  // Vercel sets x-real-ip from the TCP connection; a client can't override it.
  headers.set('X-Client-IP', request.headers.get('x-real-ip') ?? '')

  const response = await fetch(target, {
    method: request.method,
    headers,
    body: request.body,
    redirect: 'manual',
    // @ts-expect-error -- required by the fetch spec to stream a request body
    duplex: 'half',
  })

  const responseHeaders = new Headers(response.headers)
  // fetch already decompressed the body.
  responseHeaders.delete('content-encoding')
  responseHeaders.delete('content-length')

  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers: responseHeaders,
  })
}
