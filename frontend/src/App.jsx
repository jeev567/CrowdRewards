// JSX uses React in scope with the classic transform; useState stores UI state.
import React, { useEffect, useState } from "react";

// Components are reusable pieces of UI. This one shows the two brands.
// PayPal is displayed as text for the prototype; use approved assets for production.
function BrandHeader() {
  return (
    <header className="brand-bar">
      <a className="brand" href="#" aria-label="CrowdRewards home"><span className="brand-mark">✦</span> Crowd<span className="accent">Rewards</span></a>
      <div className="partner"><span className="partner-label">Payments powered by</span><strong className="paypal">PayPal</strong></div>
    </header>
  );
}

// Props are values passed from a parent component into a child component.
function CampaignCard({ campaign, onEdit, onDelete }) {
  const percent = Math.min(100, Math.round((campaign.joined / campaign.goal) * 100));
  return (
    <article className="campaign-card">
      <div className="card-icon" aria-hidden="true">🎁</div>
      <div className="card-body">
        <div className="card-top"><span className="eyebrow">GROUP REWARD</span><span className="discount">Save {campaign.discount}%</span></div>
        <h3>{campaign.title}</h3><p>{campaign.description}</p>
        <div className="progress-label"><span>{campaign.joined} of {campaign.goal} joined</span><strong>{percent}%</strong></div>
        <div className="progress-track" role="progressbar" aria-valuenow={percent} aria-valuemin="0" aria-valuemax="100" aria-label="Campaign progress"><div className="progress-fill" style={{ width: percent + "%" }} /></div>
        <div className="campaign-actions"><button className="secondary-button" onClick={() => onEdit(campaign)} disabled={campaign.joined > 0}>Edit</button><button className="secondary-button" onClick={() => onDelete(campaign)} disabled={campaign.joined > 0}>Delete</button></div>
      </div>
    </article>
  );
}

export default function App() {
  // useState stores data that can change. Updating state causes React to re-render the UI.
  const [campaigns, setCampaigns] = useState([]);
  const [merchants, setMerchants] = useState([]);
  const [merchantId, setMerchantId] = useState("");
  const [merchantName, setMerchantName] = useState("");
  const [merchantEmail, setMerchantEmail] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  // useEffect runs after the first render. Here it loads saved data from our Java API.
  useEffect(() => {
    async function loadData() {
      try {
        const [campaignResponse, merchantResponse] = await Promise.all([
          fetch("/api/campaigns"), fetch("/api/merchants")
        ]);
        if (!campaignResponse.ok || !merchantResponse.ok) throw new Error("Could not load data. Is Spring Boot running?");
        const [savedCampaigns, savedMerchants] = await Promise.all([
          campaignResponse.json(), merchantResponse.json()
        ]);
        setCampaigns(savedCampaigns);
        setMerchants(savedMerchants);
        if (savedMerchants.length) setMerchantId(String(savedMerchants[0].id));
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    }
    loadData();
  }, []); // Empty array means this effect runs on initial mount.

  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [description, setDescription] = useState("");
  const [title, setTitle] = useState("");
  const [discount, setDiscount] = useState("10");
  const [goal, setGoal] = useState("25");
  const [aiProduct, setAiProduct] = useState("");
  const [aiPrice, setAiPrice] = useState("");
  const [aiAudience, setAiAudience] = useState("");
  const [aiNotes, setAiNotes] = useState("");
  const [aiBusy, setAiBusy] = useState(false);
  const [aiRationale, setAiRationale] = useState("");

  // Event handlers run in response to user actions, such as submitting a form.
  function openCreate() {
    setEditingId(null); setTitle(""); setDescription(""); setDiscount("10"); setGoal("25"); setShowForm(true);
  }

  function openEdit(campaign) {
    setEditingId(campaign.id); setTitle(campaign.title); setDescription(campaign.description);
    setDiscount(String(campaign.discount)); setGoal(String(campaign.goal)); setShowForm(true);
  }

  async function handleDelete(campaign) {
    if (!window.confirm(`Delete "${campaign.title}"? This cannot be undone.`)) return;
    setError("");
    try {
      const response = await fetch(`/api/campaigns/${campaign.id}`, { method: "DELETE" });
      if (!response.ok) throw new Error("Could not delete campaign.");
      setCampaigns(previous => previous.filter(item => item.id !== campaign.id));
    } catch (err) { setError(err.message); }
  }

  async function handleAiSuggest() {
    setError(""); setAiBusy(true); setAiRationale("");
    try {
      const response = await fetch("/api/ai/campaign-suggestion", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          product: aiProduct.trim(), price: Number(aiPrice),
          audience: aiAudience.trim(), merchantNotes: aiNotes.trim()
        })
      });
      if (!response.ok) {
        const details = await response.json().catch(() => ({}));
        throw new Error(details.error || "AI suggestion failed. Check backend configuration.");
      }
      const suggestion = await response.json();
      setTitle(suggestion.title); setDescription(suggestion.description);
      setDiscount(String(suggestion.discount)); setGoal(String(suggestion.goal));
      setAiRationale(suggestion.rationale);
    } catch (err) { setError(err.message); }
    finally { setAiBusy(false); }
  }

  async function handleCreate(event) {
    event.preventDefault();
    setError("");
    setSaving(true);
    try {
      // POST sends JSON to Spring Boot. await pauses this function until the response arrives.
      const response = await fetch(editingId ? `/api/campaigns/${editingId}` : "/api/campaigns", {
        method: editingId ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          merchantId: Number(merchantId), title: title.trim(),
          description: description.trim(),
          discount: Number(discount), goal: Number(goal)
        })
      });
      if (!response.ok) throw new Error("Campaign could not be saved. Check the form and backend.");
      const created = await response.json();
      setCampaigns(previous => editingId ? previous.map(item => item.id === editingId ? created : item) : [created, ...previous]);
      setTitle(""); setDescription(""); setDiscount("10"); setGoal("25"); setEditingId(null); setShowForm(false);
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  async function handleCreateMerchant(event) {
    event.preventDefault();
    setError("");
    setSaving(true);
    try {
      const response = await fetch("/api/merchants", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: merchantName.trim(), email: merchantEmail.trim() })
      });
      if (!response.ok) throw new Error("Could not register merchant. Check the name/email or use another email.");
      const merchant = await response.json();
      setMerchants(previous => [merchant, ...previous]);
      setMerchantId(String(merchant.id));
      setMerchantName(""); setMerchantEmail("");
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="site">
      <BrandHeader />
      <main>
        <section className="hero">
          <div className="hero-copy"><div className="pill">✦ AI-powered group buying</div><h1>Better deals.<br/><span className="gradient-text">Together.</span></h1><p>Turn every purchase into a shared win. Create group campaigns, grow your community, and reward everyone when goals are reached.</p><button className="primary-button" onClick={openCreate}>Create a campaign <span aria-hidden="true">→</span></button><div className="hero-note">Demo only · No payments processed</div></div>
          <div className="hero-visual" aria-label="CrowdRewards and PayPal brand introduction"><div className="orbit orbit-one"/><div className="orbit orbit-two"/><div className="visual-center"><div className="visual-logo">✦</div><strong>CrowdRewards</strong><span className="visual-plus">+</span><strong className="paypal">PayPal</strong><small>Grow together. Reward together.</small></div></div>
        </section>
        <section className="campaign-section" id="campaigns"><div className="section-heading"><div><div className="eyebrow">MERCHANT STUDIO</div><h2>Campaigns</h2><p>Manage your group-buying ideas in one place.</p></div><button className="secondary-button" onClick={openCreate}>+ New campaign</button></div>
          {error && <p role="alert" style={{color:"#b42318"}}>{error}</p>}
          {loading ? <p>Loading campaigns…</p> : campaigns.length === 0 ? <p>No campaigns yet. Register a merchant and create your first campaign.</p> : <div className="campaign-grid">{campaigns.map(campaign => <CampaignCard key={campaign.id} campaign={campaign} onEdit={openEdit} onDelete={handleDelete} />)}</div>}
          <section className="merchant-panel"><h3>Merchant registration</h3><p>Register a merchant to own new campaigns.</p>
            <form onSubmit={handleCreateMerchant} className="merchant-form">
              <label>Merchant name<input required maxLength={120} value={merchantName} onChange={event => setMerchantName(event.target.value)} placeholder="Your store name" /></label>
              <label>Email<input required type="email" maxLength={254} value={merchantEmail} onChange={event => setMerchantEmail(event.target.value)} placeholder="store@example.com" /></label>
              <button className="secondary-button" disabled={saving}>Register merchant</button>
            </form>
          </section>
        </section>
      </main>
      <footer>© {new Date().getFullYear()} CrowdRewards · PayPal Sandbox integration coming in a later milestone.</footer>
      {showForm && <div className="modal-backdrop" onClick={() => setShowForm(false)}><section className="modal" role="dialog" aria-modal="true" aria-labelledby="form-title" onClick={event => event.stopPropagation()}><div className="modal-heading"><h2 id="form-title">{editingId ? "Edit campaign" : "New campaign"}</h2><button className="icon-button" aria-label="Close form" onClick={() => setShowForm(false)}>×</button></div><p>Create a demo campaign. Campaigns are saved to SQLite through Spring Boot.</p>{error && <p role="alert" style={{color:"#b42318"}}>{error}</p>}
        {!editingId && <div className="ai-architect">
          <h3>✦ AI Campaign Architect</h3>
          <p>Describe your product and let AI draft a campaign. Review the numbers before saving.</p>
          <label>Product or service<input maxLength={120} value={aiProduct} onChange={event => setAiProduct(event.target.value)} placeholder="e.g. Premium coffee bundle" /></label>
          <label>Price (USD)<input type="number" min="0.01" max="1000000" step="0.01" value={aiPrice} onChange={event => setAiPrice(event.target.value)} placeholder="49.99" /></label>
          <label>Target audience<input maxLength={500} value={aiAudience} onChange={event => setAiAudience(event.target.value)} placeholder="e.g. Local coffee lovers" /></label>
          <label>Merchant notes<textarea maxLength={500} value={aiNotes} onChange={event => setAiNotes(event.target.value)} placeholder="Optional campaign constraints" /></label>
          <button className="secondary-button" type="button" disabled={aiBusy || !aiProduct.trim() || !(Number(aiPrice) > 0)} onClick={handleAiSuggest}>{aiBusy ? "Generating…" : "✦ Generate with AI"}</button>
          {aiRationale && <p className="ai-rationale"><strong>AI reasoning:</strong> {aiRationale}</p>}
        </div>}
        <form onSubmit={handleCreate}><label>Merchant<select required value={merchantId} onChange={event => setMerchantId(event.target.value)}><option value="">Select merchant</option>{merchants.map(merchant => <option key={merchant.id} value={merchant.id}>{merchant.name}</option>)}</select></label><label>Campaign name<input required maxLength={80} value={title} onChange={event => setTitle(event.target.value)} placeholder="e.g. Weekend Coffee Bundle" /></label><label>Description<textarea maxLength={500} value={description} onChange={event => setDescription(event.target.value)} /></label><label>Discount percentage<input required type="number" min="1" max="90" step="1" value={discount} onChange={event => setDiscount(event.target.value)} /></label><label>Group target<input required type="number" min="2" step="1" value={goal} onChange={event => setGoal(event.target.value)} /></label><button className="primary-button" type="submit" disabled={saving || !merchantId}>{saving ? "Saving…" : editingId ? "Save changes →" : "Create campaign →"}</button></form></section></div>}
    </div>
  );
}
