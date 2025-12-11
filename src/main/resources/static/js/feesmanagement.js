// ===== Fees & Installments Dynamic Logic =====
(function () {
  const totalFeesEl = document.getElementById('totalFees');
  const discountEl = document.getElementById('discountAmount');
  const actualFeesEl = document.getElementById('actualFees');
  const countEl = document.getElementById('installmentsCount');
  const tbody = document.getElementById('installmentsBody');
  const sumEl = document.getElementById('installmentsSum');
  const evenSplitBtn = document.getElementById('evenSplitBtn');

  if (!totalFeesEl || !discountEl || !actualFeesEl || !countEl || !tbody || !sumEl) return;

  const MODES = ['Cash', 'UPI', 'Card', 'Bank Transfer', 'Cheque/DD'];

  function toNumber(v) {
    const n = Number(v);
    return Number.isFinite(n) ? n : 0;
  }

  function recalcActual() {
    const total = toNumber(totalFeesEl.value);
    const disc = Math.min(toNumber(discountEl.value), total);
    const actual = Math.max(total - disc, 0);
    actualFeesEl.value = actual;
    recalcInstallmentsSum();
  }

  function recalcInstallmentsSum() {
    let sum = 0;
    tbody.querySelectorAll('input[data-field="amount"]').forEach(inp => {
      sum += toNumber(inp.value);
    });
    sumEl.value = sum;
    // Optional: add a soft warning style if mismatch
    const actual = toNumber(actualFeesEl.value);
    sumEl.classList.toggle('is-invalid', actual && sum !== actual);
  }

  function buildModeSelect(nameId) {
    const sel = document.createElement('select');
    sel.className = 'form-select';
    sel.setAttribute('data-field', 'mode');
    sel.id = nameId;
    MODES.forEach(m => {
      const opt = document.createElement('option');
      opt.value = m;
      opt.textContent = m;
      sel.appendChild(opt);
    });
    return sel;
  }

  function buildRow(i) {
    const tr = document.createElement('tr');

    // Sr no
    const tdSr = document.createElement('td');
    tdSr.textContent = String(i + 1);
    tr.appendChild(tdSr);

    // Amount
    const tdAmt = document.createElement('td');
    const amt = document.createElement('input');
    amt.type = 'number';
    amt.min = '0';
    amt.step = '1';
    amt.className = 'form-control';
    amt.placeholder = '0';
    amt.setAttribute('data-field', 'amount');
    amt.id = `inst_${i}_amount`;
    amt.addEventListener('input', recalcInstallmentsSum);
	
	attachAmountListeners(i, amt);
    tdAmt.appendChild(amt);
    tr.appendChild(tdAmt);

    // Date
    const tdDate = document.createElement('td');
    const date = document.createElement('input');
    date.type = 'date';
    date.className = 'form-control';
    date.setAttribute('data-field', 'date');
    date.id = `inst_${i}_date`;
    tdDate.appendChild(date);
    tr.appendChild(tdDate);

    // Mode
    const tdMode = document.createElement('td');
    const modeSel = buildModeSelect(`inst_${i}_mode`);
    tdMode.appendChild(modeSel);
    tr.appendChild(tdMode);

    // Receipt (file)
    const tdFile = document.createElement('td');
    const file = document.createElement('input');
    file.type = 'file';
    file.className = 'form-control';
    file.accept = '.pdf,.jpg,.jpeg,.png';
    file.setAttribute('data-field', 'receipt');
    file.id = `inst_${i}_file`;
    tdFile.appendChild(file);
    tr.appendChild(tdFile);

    return tr;
  }

  function renderRows(count) {
    tbody.innerHTML = '';
    const n = Math.max(0, toNumber(count));
    for (let i = 0; i < n; i++) {
      tbody.appendChild(buildRow(i));
    }
    recalcInstallmentsSum();
  }

  // Evenly split amounts across rows to match Actual Fees
  function evenSplit() {
    const n = tbody.querySelectorAll('tr').length;
    if (n === 0) return;
    const total = toNumber(actualFeesEl.value);
    if (!total) return;

    const base = Math.floor(total / n);
    let remainder = total - base * n;

    tbody.querySelectorAll('input[data-field="amount"]').forEach((inp, idx) => {
      let val = base;
      if (remainder > 0) {
        val += 1; // distribute remainder by +1 to first rows
        remainder -= 1;
      }
      inp.value = val;
    });
    recalcInstallmentsSum();
  }

  // Expose a helper to extract installments data and files later when submitting
  window.getInstallmentsData = function () {
    const rows = [...tbody.querySelectorAll('tr')];
    return rows.map((tr, idx) => {
      const amount = toNumber(tr.querySelector('input[data-field="amount"]')?.value);
      const date = tr.querySelector('input[data-field="date"]')?.value || null;
      const mode = tr.querySelector('select[data-field="mode"]')?.value || null;
      const file = tr.querySelector('input[data-field="receipt"]')?.files?.[0] || null;
      return { srNo: idx + 1, amount, date, mode, file };
    });
  };

  // Wire events
  totalFeesEl.addEventListener('input', recalcActual);
  discountEl.addEventListener('input', recalcActual);
  countEl.addEventListener('change', e => renderRows(e.target.value));
  evenSplitBtn?.addEventListener('click', evenSplit);

  // Initial compute
  recalcActual();
  renderRows(countEl.value);
  
  
  // === Add inside the IIFE that manages the fees table ===

  // Helper: fetch all amount inputs in order
  function getAmountInputs() {
    return [...tbody.querySelectorAll('input[data-field="amount"]')];
  }

  // Redistribute a delta from row `startIdx` to all rows below equally.
  // delta = (newAmount - oldAmount). Positive means "take from below".
  // Negative means "give to below".
  function redistributeDeltaFrom(startIdx, delta) {
    const amountInputs = getAmountInputs();
    const n = amountInputs.length;
    const below = amountInputs.slice(startIdx + 1); // rows strictly below
    const m = below.length;
    if (m === 0 || !Number.isFinite(delta) || delta === 0) {
      recalcInstallmentsSum();
      return;
    }

    let remaining = Math.trunc(Math.round(delta)); // integer rupees
    const sign = remaining > 0 ? 1 : -1;
    const abs = Math.abs(remaining);

    const base = Math.floor(abs / m);
    let rem = abs - base * m;

    // For delta > 0: subtract from below
    // For delta < 0: add to below
    for (let i = 0; i < m; i++) {
      const inp = below[i];
      const cur = toNumber(inp.value);
      let adj = base + (rem > 0 ? 1 : 0);
      if (rem > 0) rem--;

      if (sign > 0) {
        // subtract
        const nextVal = Math.max(cur - adj, 0);
        const actuallySubtracted = cur - nextVal;
        remaining -= actuallySubtracted; // track if we hit zero caps
        inp.value = nextVal;
      } else {
        // add
        inp.value = cur + adj;
        remaining += adj; // remaining is negative; adding reduces magnitude (or you can ignore tracking)
      }
    }

    // If we couldn't subtract enough because some EMIs hit zero,
    // try another pass to take the leftover from non-zero rows.
    if (sign > 0 && remaining > 0) {
      let safety = 5; // avoid infinite loops; typically 1 extra pass is enough
      while (remaining > 0 && safety-- > 0) {
        const nonZeroBelow = below.filter(inp => toNumber(inp.value) > 0);
        if (nonZeroBelow.length === 0) break;
        const per = Math.max(1, Math.floor(remaining / nonZeroBelow.length));
        for (const inp of nonZeroBelow) {
          const cur = toNumber(inp.value);
          const sub = Math.min(cur, per);
          inp.value = cur - sub;
          remaining -= sub;
          if (remaining <= 0) break;
        }
      }
    }

    recalcInstallmentsSum();
  }

  // Hook prev values so we can detect the delta
  function attachAmountListeners(rowIndex, amountInput) {
    // store previous value on focus
    amountInput.addEventListener('focus', () => {
      amountInput.dataset.prev = String(toNumber(amountInput.value));
    });

    // when user types, redistribute the difference to rows below
    amountInput.addEventListener('input', () => {
      const prev = toNumber(amountInput.dataset.prev ?? amountInput.value);
      const now = toNumber(amountInput.value);
      const delta = now - prev;
      // update prev so continuous typing works smoothly
      amountInput.dataset.prev = String(now);
      // push/pull delta to rows below
      redistributeDeltaFrom(rowIndex, delta);
    });
  }

})();
