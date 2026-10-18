import { useState } from "react";

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
function CampaignCard({ campaign }) {
  const percent = Math.min(100, Math.round((campaign.joined / campaign.goal) * 100));
  return (
    <article className="campaign-card">
      <div className="card-icon" aria-hidden="true">🎁</div>
      <div className="card-body">
        <div className="card-top"><span className="eyebrow">GROUP REWARD</span><span className="discount">Save {campaign.discount}%</span></div>
        <h3>{campaign.title}</h3><p>{campaign.description}</p>
        <div className="progress-label"><span>{campaign.joined} of {campaign.goal} joined</span><strong>{percent}%</strong></div>
        <div className="progress-track" role="progressbar" aria-valuenow={percent} aria-valuemin="0" aria-valuemax="100" aria-label="Campaign progress"><div className="progress-fill" style={{ width: percent + "%" }} /></div>
      </div>
    </article>
  );
}

const starterCampaigns = [
  { id: 1, title: "Weekend Coffee Bundle", description: "Unlock a better price when coffee lovers shop together.", discount: 15, joined: 32, goal: 50 },
  { id: 2, title: "Wireless Headphones", description: "A group reward for your next audio upgrade.", discount: 20, joined: 18, goal: 40 },
  { id: 3, title: "Fitness Essentials", description: "Team up to unlock savings on everyday workout gear.", discount: 12, joined: 44, goal: 60 }
];

export default function App() {
  // useState stores data that can change. Updating state causes React to re-render the UI.
  const [campaigns, setCampaigns] = useState(starterCampaigns);
  const [showForm, setShowForm] = useState(false);
  const [title, setTitle] = useState("");
  const [discount, setDiscount] = useState("10");
  const [goal, setGoal] = useState("25");

  // Event handlers run in response to user actions, such as submitting a form.
  function handleCreate(event) {
    event.preventDefault(); // Prevents the browser from reloading the page.
    const cleanTitle = title.trim();
    const parsedDiscount = Number(discount);
    const parsedGoal = Number(goal);
    if (!cleanTitle || !Number.isInteger(parsedDiscount) || parsedDiscount < 1 || parsedDiscount > 90 || !Number.isInteger(parsedGoal) || parsedGoal < 2) return;
    // Spread syntax (...) creates a new array instead of mutating React state.
    setCampaigns(previous => [{ id: crypto.randomUUID(), title: cleanTitle, description: "Your new campaign is ready for the next step.", discount: parsedDiscount, joined: 0, goal: parsedGoal }, ...previous]);
    setTitle(""); setDiscount("10"); setGoal("25"); setShowForm(false);
  }

  return (
    <div className="site">
      <BrandHeader />
      <main>
        <section className="hero">
          <div className="hero-copy"><div className="pill">✦ AI-powered group buying</div><h1>Better deals.<br/><span className="gradient-text">Together.</span></h1><p>Turn every purchase into a shared win. Create group campaigns, grow your community, and reward everyone when goals are reached.</p><button className="primary-button" onClick={() => setShowForm(true)}>Create a campaign <span aria-hidden="true">→</span></button><div className="hero-note">Demo only · No payments processed</div></div>
          <div className="hero-visual" aria-label="CrowdRewards and PayPal brand introduction"><div className="orbit orbit-one"/><div className="orbit orbit-two"/><div className="visual-center"><div className="visual-logo">✦</div><strong>CrowdRewards</strong><span className="visual-plus">+</span><strong className="paypal">PayPal</strong><small>Grow together. Reward together.</small></div></div>
        </section>
        <section className="campaign-section" id="campaigns"><div className="section-heading"><div><div className="eyebrow">MERCHANT STUDIO</div><h2>Campaigns</h2><p>Manage your group-buying ideas in one place.</p></div><button className="secondary-button" onClick={() => setShowForm(true)}>+ New campaign</button></div>
          <div className="campaign-grid">{campaigns.map(campaign => <CampaignCard key={campaign.id} campaign={campaign} />)}</div>
        </section>
      </main>
      <footer>© {new Date().getFullYear()} CrowdRewards · PayPal Sandbox integration coming in a later milestone.</footer>
      {showForm && <div className="modal-backdrop" onClick={() => setShowForm(false)}><section className="modal" role="dialog" aria-modal="true" aria-labelledby="form-title" onClick={event => event.stopPropagation()}><div className="modal-heading"><h2 id="form-title">New campaign</h2><button className="icon-button" aria-label="Close form" onClick={() => setShowForm(false)}>×</button></div><p>Create a demo campaign. Data stays in memory until the page reloads.</p><form onSubmit={handleCreate}><label>Campaign name<input required maxLength={80} value={title} onChange={event => setTitle(event.target.value)} placeholder="e.g. Weekend Coffee Bundle" /></label><label>Discount percentage<input required type="number" min="1" max="90" step="1" value={discount} onChange={event => setDiscount(event.target.value)} /></label><label>Group target<input required type="number" min="2" step="1" value={goal} onChange={event => setGoal(event.target.value)} /></label><button className="primary-button" type="submit">Create campaign →</button></form></section></div>}
    </div>
  );
}
