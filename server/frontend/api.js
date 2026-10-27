let pendingRefresh;

async function send(url, method, body) {
  return fetch('/api' + url, {
    method,
    credentials: 'same-origin',
    headers: {'Content-Type': 'application/json', 'X-Requested-With': 'TOEICAdmin'},
    ...(body === undefined ? {} : {body: JSON.stringify(body)})
  });
}

export async function api(url, method = 'GET', body) {
  let response = await send(url, method, body);
  const canRefresh = response.status === 401 && !['/auth/login', '/auth/logout', '/auth/refresh', '/auth/register'].includes(url);

  if (canRefresh) {
    pendingRefresh ??= send('/auth/refresh', 'POST', {}).finally(() => {pendingRefresh = undefined;});
    const refreshed = await pendingRefresh;
    if (refreshed.ok) response = await send(url, method, body);
  }

  if (response.status === 204) return null;
  const data = await response.json();
  if (!response.ok) throw new Error([data.error, ...(data.details || [])].join('\n'));
  return data;
}
