import React, { useEffect, useState } from "react";
import MerchantApp from "./MerchantApp";

function CustomerApp() {
  const [campaigns, setCampaigns] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [joining, setJoining] = useState(null);
  const [receipt, setReceipt] = useState(null);
  const [receiptBusy, setReceiptBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [selectedId, setSelectedId] = useState(() => {
    const match = window.location.pathname.match(/^\/campaigns\/(\d+)\/?$/);
    return match ? Number(match[1]) : null;
  });
  const [showReceipt, setShowReceipt] = useState(window.location.pathname === "/purchase/success");

  async function loadCampaigns() {
    try {
      const response = await fetch("/api/campaigns");
      if (!response.ok) throw new Error("Unable to load campaigns");
      setCampaigns(await response.json());
    } catch (err) { setError(err.message); }
    finally { setLoading(false); }
  }

  async function loadReceipt(orderId) {
    setReceiptBusy(true);
    try {
      const response = await fetch("/api/checkout/orders/" + encodeURIComponent(orderId) + "/receipt");
      if (!response.ok) throw new Error("Unable to load your receipt");
      setReceipt(await response.json());
    } catch (err) { setError(err.message); }
    finally { setReceiptBusy(false); }
  }

  useEffect(() => {
    loadCampaigns();
    const params = new URLSearchParams(window.location.search);
    const token = params.get("token");
    const stored = sessionStorage.getItem("crowdrewards-paypal-order");
    if (params.has("cancel")) {
      setNotice("PayPal checkout was cancelled. No purchase was recorded.");
      window.history.replaceState({}, "", "/");
      return;
    }
    if (token && stored && token === stored) {
      window.history.replaceState({}, "", "/purchase/success");
      setShowReceipt(true);
      (async () => {
        setReceiptBusy(true);
        try {
          const response = await fetch("/api/checkout/orders/" + encodeURIComponent(token) + "/capture", { method: "POST" });
          if (!response.ok) throw new Error("Payment confirmation could not be completed. Contact support before paying again.");
          sessionStorage.setItem("crowdrewards-last-order", token);
          sessionStorage.removeItem("crowdrewards-paypal-order");
          await loadReceipt(token);
          await loadCampaigns();
        } catch (err) { setError(err.message); setReceiptBusy(false); }
      })();
    } else if (window.location.pathname === "/purchase/success") {
      const lastOrder = sessionStorage.getItem("crowdrewards-last-order");
      if (lastOrder) loadReceipt(lastOrder);
      else setNotice("No recent purchase found in this browser.");
    }
  }, []);

  useEffect(() => {
    const handlePop = () => {
      const match = window.location.pathname.match(/^\/campaigns\/(\d+)\/?$/);
      setSelectedId(match ? Number(match[1]) : null);
      setShowReceipt(window.location.pathname === "/purchase/success");
    };
    window.addEventListener("popstate", handlePop);
    return () => window.removeEventListener("popstate", handlePop);
  }, []);

  function navigate(path) {
    window.history.pushState({}, "", path);
    const match = path.match(/^\/campaigns\/(\d+)$/);
    setSelectedId(match ? Number(match[1]) : null);
    setShowReceipt(path === "/purchase/success");
    setError(""); setNotice("");
    window.scrollTo(0, 0);
  }

  async function join(campaign) {
    setError(""); setJoining(campaign.id);
    try {
      const response = await fetch("/api/checkout/orders", {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ campaignId: campaign.id })
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.message || data.error || "Could not start PayPal checkout");
      sessionStorage.setItem("crowdrewards-paypal-order", data.orderId);
      window.location.assign(data.approvalUrl);
    } catch (err) { setError(err.message); setJoining(null); }
  }

  const selected = campaigns.find(c => c.id === selectedId);
  const visible = selectedId ? (selected ? [selected] : []) : campaigns;
  return <div className="site customer-site">
    <header className="brand-bar">
      <a className="brand" href="/" onClick={e => { e.preventDefault(); navigate("/"); }}><span className="brand-mark">✦</span> Crowd<span className="accent">Rewards</span></a>
      <nav className="customer-nav"><a href="/" onClick={e => { e.preventDefault(); navigate("/"); }}>Explore deals</a><a href="/merchant">Merchant studio →</a></nav>
    </header>
    <main className="customer-main">
      {!showReceipt && <section className="customer-hero"><span className="pill">GROUP BUYING, BETTER TOGETHER</span><h1>{selectedId ? "A better deal, together." : "Unlock rewards with your crowd."}</h1><p>Pay the listed price with PayPal Sandbox. When enough purchases are completed, everyone earns their advertised partial refund.</p></section>}
      {error && <p role="alert" className="customer-error">{error}</p>}
      {notice && <p role="status">{notice}</p>}
      {showReceipt ? <section className="customer-receipt">
        <div className="receipt-success-icon">✓</div><div className="eyebrow">PURCHASE STATUS</div><h1>{receipt?.paymentStatus === "COMPLETED" ? "You're in!" : "Your purchase"}</h1>
        <p>Track your group reward right here.</p>
        {receiptBusy && <p>Checking PayPal payment and refund status…</p>}
        {receipt && <div className="receipt-details">
          <div><span>Campaign</span><strong>{receipt.campaignTitle}</strong></div>
          <div><span>Paid today</span><strong>{"$" + Number(receipt.paidAmount).toFixed(2)}</strong></div>
          <div><span>Group progress</span><strong>{receipt.joined} / {receipt.goal}</strong></div>
          <div><span>Payment</span><strong>{receipt.paymentStatus}</strong></div>
          <div><span>Refund status</span><strong>{receipt.refundStatus.replaceAll("_", " ")}</strong></div>
          <div><span>Refund amount</span><strong>{"$" + Number(receipt.refundAmount).toFixed(2)}</strong></div>
        </div>}
        <div className="customer-actions"><button className="primary-button" disabled={receiptBusy || !sessionStorage.getItem("crowdrewards-last-order")} onClick={() => loadReceipt(sessionStorage.getItem("crowdrewards-last-order"))}>Refresh refund status</button><button className="secondary-button" onClick={() => navigate("/")}>Explore more deals</button></div>
        <p className="receipt-note">Sandbox demonstration only. A refund is issued after the campaign goal is reached and PayPal completes processing.</p>
      </section> : <>
        <div className="customer-section-heading"><div className="eyebrow">AVAILABLE CAMPAIGNS</div><h2>{selectedId ? "Campaign details" : "Find your next group deal"}</h2></div>
        {selectedId && <button className="secondary-button customer-back" onClick={() => navigate("/")}>← All campaigns</button>}
        {loading ? <p>Loading deals…</p> : selectedId && !selected ? <p>Campaign not found.</p> : visible.length === 0 ? <p>No deals are available yet. Check back soon.</p> :
        <div className="customer-grid">{visible.map(c => {
          const joined = Number(c.joined || 0), goal = Number(c.goal || 1), percent = Math.min(100, Math.round(joined / goal * 100));
          const refund = Number(c.price || 0) * Number(c.discount || 0) / 100;
          const full = joined >= goal;
          return <article className="customer-deal" key={c.id}>
            <div className="customer-deal-banner">🎁<span>{c.discount}% GROUP REWARD</span></div>
            <div className="customer-deal-body"><h3>{c.title}</h3><p>{c.description}</p>
              <div className="customer-prices"><div><small>Pay today</small><strong>{"$" + Number(c.price).toFixed(2)}</strong></div><div><small>Refund if goal met</small><strong className="customer-green">{"$" + refund.toFixed(2)}</strong></div></div>
              <div className="progress-label"><span>{joined} of {goal} purchases</span><strong>{percent}%</strong></div>
              <div className="progress-track"><div className="progress-fill" style={{ width: percent + "%" }}/></div>
              <p className="customer-deal-note">{full ? "Group target reached" : (goal - joined) + " more purchases needed to unlock the reward"}</p>
              <div className="customer-actions"><button className="primary-button" disabled={full || joining === c.id || Number(c.price) <= 0} onClick={() => join(c)}>{full ? "Goal reached" : joining === c.id ? "Opening PayPal…" : "Join with PayPal"}</button>{!selectedId && <button className="secondary-button" onClick={() => navigate("/campaigns/" + c.id)}>View details</button>}</div>
            </div>
          </article>;
        })}</div>}
      </>}
    </main>
    <footer>© {new Date().getFullYear()} CrowdRewards · PayPal Sandbox demo · No real payments</footer>
  </div>;
}

export default function App() {
  return window.location.pathname.startsWith("/merchant") ? <MerchantApp /> : <CustomerApp />;
}
