import React, { useState } from 'react';

const API_BASE = process.env.REACT_APP_API_BASE_URL || '';

async function postFile(path, file) {
  const fd = new FormData();
  fd.append('file', file);
  const res = await fetch(`${API_BASE}${path}`, { method: 'POST', body: fd });
  if (!res.ok) throw new Error(await res.text());
  return res.text();
}

export default function App() {
  const [txFile, setTxFile] = useState(null);
  const [refFile, setRefFile] = useState(null);
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(false);

  const handleRun = async () => {
    setLoading(true);
    setReport(null);
    try {
      if (txFile) await postFile('/api/upload/transactions', txFile);
      if (refFile) await postFile('/api/upload/reference', refFile);
      const r = await fetch(`${API_BASE}/api/reconcile`);
      const json = await r.json();
      setReport(json);
    } catch (e) {
      alert(e.message || 'Error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ padding: 24, fontFamily: 'system-ui, sans-serif', maxWidth: 900 }}>
      <h2>Transaction Reconciler</h2>
      <p>Upload two CSVs, then run reconciliation.</p>

      <div style={{ marginBottom: 12 }}>
        <label>Transactions CSV: </label>
        <input type="file" accept=".csv" onChange={e => setTxFile(e.target.files[0])} />
      </div>
      <div style={{ marginBottom: 12 }}>
        <label>Reference CSV: </label>
        <input type="file" accept=".csv" onChange={e => setRefFile(e.target.files[0])} />
      </div>

      <button onClick={handleRun} disabled={loading}>Run Reconciliation</button>
      {loading && <p>Running…</p>}

      {report && (
        <div style={{ marginTop: 20 }}>
          <h3>Summary</h3>
          <ul>
            <li>Total transactions: {report.totalTransactions}</li>
            <li>Total references: {report.totalReferences}</li>
            <li>Matched: {report.matched}</li>
            <li>Unmatched transactions: {report.unmatchedTransactions}</li>
            <li>Unmatched references: {report.unmatchedReferences}</li>
          </ul>

          <h3>Matches (first 50)</h3>
          <table border="1" cellPadding="6" width="100%">
            <thead>
              <tr>
                <th>txnId</th><th>txnAmount</th><th>txnDate</th><th>referenceId</th><th>rule</th>
              </tr>
            </thead>
            <tbody>
              {report.matches.slice(0, 50).map((m, i) => (
                <tr key={i}>
                  <td>{m.txnId}</td>
                  <td>{m.txnAmount}</td>
                  <td>{m.txnDate}</td>
                  <td>{m.referenceId || '—'}</td>
                  <td>{m.rule}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
