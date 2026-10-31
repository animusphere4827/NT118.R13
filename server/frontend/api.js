let pendingRefresh;

async function send(url, method, body, options = {}) {
  return fetch('/api' + url, {
    method,
    credentials: 'same-origin',
    headers: {'Content-Type': 'application/json', 'X-Requested-With': 'TOEICAdmin', ...options.headers},
    ...(body === undefined ? {} : {body: JSON.stringify(body)})
  });
}

export async function api(url, method = 'GET', body, options = {}) {
  let response = await send(url, method, body, options);
  const canRefresh = response.status === 401 && !['/auth/login', '/auth/logout', '/auth/refresh', '/auth/register'].includes(url);

  if (canRefresh) {
    pendingRefresh ??= send('/auth/refresh', 'POST', {}).finally(() => {pendingRefresh = undefined;});
    const refreshed = await pendingRefresh;
    if (refreshed.ok) response = await send(url, method, body, options);
  }

  if (response.status === 204) return null;
  if (response.status === 401 && !['/auth/login','/auth/register'].includes(url) && typeof window !== 'undefined') {
    window.dispatchEvent(new Event('toeic:session-expired'));
  }
  let data;
  try { data = await response.json(); }
  catch { throw Object.assign(new Error('Máy chủ trả phản hồi không hợp lệ. Hãy thử lại.'),{status:response.status}); }
  if (!response.ok) throw Object.assign(new Error([data.error, ...(data.details || [])].join('\n')),{status:response.status,details:data.details||[]});
  return data;
}
