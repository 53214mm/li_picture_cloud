// These POST endpoints are queries. Every other non-read method remains a write,
// including unknown endpoints: the no-accidental-mutation assertions fail closed.
const readOnlyPosts = new Set([
  '/api/picture/list/page/vo/cache',
  '/api/space/list/page/vo'
])

export function isBusinessWrite({ method, path }) {
  return !['GET', 'HEAD', 'OPTIONS'].includes(method)
    && !(method === 'POST' && readOnlyPosts.has(path))
}
