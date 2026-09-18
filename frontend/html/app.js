const $ = (id) => document.getElementById(id);
const time = (iso) => (iso ? new Date(iso).toLocaleTimeString() : '');
const cell = (value, className) => {
  const td = document.createElement('td');
  td.textContent = value ?? '';
  if (className) td.className = className;
  return td;
};
const row = (...cells) => {
  const tr = document.createElement('tr');
  tr.append(...cells);
  return tr;
};

async function refresh() {
  try {
    const [stock, orders] = await Promise.all([
      fetch('/api/stock').then((r) => r.json()),
      fetch('/api/orders').then((r) => r.json()),
    ]);
    $('stock').replaceChildren(...stock.map((s) =>
      row(cell(s.sku), cell(s.available), cell(s.warehouse), cell(time(s.updatedAt)))));
    $('orders').replaceChildren(...orders.map((o) =>
      row(cell(time(o.createdAt)), cell(o.sku), cell(o.quantity),
          cell(o.totalAmount != null ? `${o.totalAmount} ${o.currency}` : '—'),
          cell(o.rejectionReason ? `${o.status} (${o.rejectionReason})` : o.status, o.status))));
  } catch (e) {
    console.warn('refresh failed', e);
  }
}

$('order-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = new FormData(event.target);
  const response = await fetch('/api/orders', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sku: form.get('sku'), quantity: Number(form.get('quantity')) }),
  });
  const body = await response.json();
  const result = $('result');
  if (response.ok) {
    result.textContent = `Order ${body.id}: ${body.status}${body.rejectionReason ? ` (${body.rejectionReason})` : ''}`;
    result.className = body.status === 'ACCEPTED' ? 'ok' : 'ko';
  } else {
    result.textContent = `Error ${response.status}: ${body.detail ?? body.title}`;
    result.className = 'ko';
  }
  refresh();
});

refresh();
setInterval(refresh, 3000);
