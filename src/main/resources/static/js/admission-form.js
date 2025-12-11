// serialize dynamic fees rows into indexed form fields before submit
document.addEventListener('DOMContentLoaded', function(){
  const form = document.getElementById('admissionForm');
  form.addEventListener('submit', function(e){
    const tbody = document.querySelector('#feesTable tbody');
    const rows = Array.from(tbody.querySelectorAll('tr'));
    // remove previously added hidden fee inputs
    document.querySelectorAll('input[name^="fees["]').forEach(n=>n.remove());
    rows.forEach((tr, idx)=>{
      const year = tr.querySelector('input[name="studyYear"]').value;
      const inst = tr.querySelector('input[name="installmentNo"]').value;
      const amt = tr.querySelector('input[name="amount"]').value;
      const due = tr.querySelector('input[name="dueDate"]').value;
      const container = document.createElement('div');
      container.style.display='none';
      container.innerHTML = `
        <input name="fees[${idx}].studyYear" value="${year}">
        <input name="fees[${idx}].installmentNo" value="${inst}">
        <input name="fees[${idx}].amount" value="${amt}">
        <input name="fees[${idx}].dueDate" value="${due}">`;
      form.appendChild(container);
    });
  });
});