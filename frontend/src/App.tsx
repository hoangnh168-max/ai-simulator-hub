
import { useEffect, useMemo, useState, type FormEvent } from 'react';
import {
  Activity, ArrowUpRight, BookOpen, CheckCircle2, ChevronDown,
  CircleHelp, Clock3, Code2, ExternalLink, FilePlus2, Filter, FlaskConical, GraduationCap, LayoutDashboard,
  Library, LifeBuoy, Menu, MoreHorizontal, Plus, Search, Settings,
  ShieldCheck, Sparkles, Users, X, Zap
} from 'lucide-react';
import {
  getSimulators,
  createSimulator,
  approveSimulator,
  type Simulator
} from './services/api';
import './App.css';

const navigation = [
  { label: 'Dashboard', icon: LayoutDashboard },
  { label: 'Simulator Library', icon: Library },
  { label: 'Submit Simulator', icon: FilePlus2 },
  { label: 'My Simulators', icon: BookOpen },
  { label: 'Administration', icon: ShieldCheck }
];

function App() {
  const [page, setPage] = useState('Dashboard');
  const [simulators, setSimulators] = useState<Simulator[]>([]);
  const [loading, setLoading] = useState(true);
  const [apiError, setApiError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [approvingId, setApprovingId] = useState<string | null>(null);

  // This tracks submissions made during the current browser session.
  // Proper ownership will be implemented with user authentication later.
  const [mySimulatorIds, setMySimulatorIds] = useState<string[]>([]);

  const [search, setSearch] = useState('');
  const [subject, setSubject] = useState('All subjects');
  const [mobileMenu, setMobileMenu] = useState(false);
  const [selected, setSelected] = useState<Simulator | null>(null);
  const [message, setMessage] = useState('');

  const [form, setForm] = useState({
    title: '',
    url: '',
    subject: 'Physics',
    level: 'Grades 9–12',
    description: ''
  });

  // Load simulator records from the Spring Boot backend.
  useEffect(() => {
    let cancelled = false;

    async function loadSimulators() {
      try {
        setLoading(true);
        setApiError('');

        const data = await getSimulators();

        if (!cancelled) {
          setSimulators(data);
        }
      } catch (error) {
        if (!cancelled) {
          setApiError(
            error instanceof Error
              ? error.message
              : 'Unable to connect to the backend.'
          );
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void loadSimulators();

    return () => {
      cancelled = true;
    };
  }, []);

  const filtered = useMemo(() => simulators.filter((simulator) => {
    const matchesSearch =
      `${simulator.title} ${simulator.description} ${simulator.subject}`
        .toLowerCase()
        .includes(search.toLowerCase());

    const matchesSubject =
      subject === 'All subjects' || simulator.subject === subject;

    return matchesSearch && matchesSubject;
  }), [simulators, search, subject]);

  const publishedSimulators = simulators.filter(
    simulator => simulator.status === 'Published'
  );

  const publishedCount = publishedSimulators.length;
  const pendingCount = simulators.filter(
    simulator => simulator.status === 'Pending'
  ).length;

  function navigate(label: string) {
    setPage(label);
    setSelected(null);
    setSearch('');
    setMessage('');
    setApiError('');
    setMobileMenu(false);
  }

  // Submit a new simulator to PostgreSQL through the API.
  async function submitSimulator(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    setMessage('');
    setApiError('');
    setSubmitting(true);

    try {
      const created = await createSimulator({
        title: form.title.trim(),
        description: form.description.trim(),
        simulatorUrl: form.url.trim(),
        subject: form.subject,
        educationLevel: form.level
      });

      setSimulators(previous => [created, ...previous]);
      setMySimulatorIds(previous => [created.id, ...previous]);

      setForm({
        title: '',
        url: '',
        subject: 'Physics',
        level: 'Grades 9–12',
        description: ''
      });

      setMessage('Your simulator has been submitted successfully and is awaiting review.');
      setPage('My Simulators');
    } catch (error) {
      setApiError(
        error instanceof Error
          ? error.message
          : 'Unable to submit the simulator. Please try again.'
      );
    } finally {
      setSubmitting(false);
    }
  }

  // Approve a pending simulator through the backend.
  async function handleApprove(id: string) {
    setApiError('');
    setMessage('');
    setApprovingId(id);

    try {
      const updated = await approveSimulator(id);

      setSimulators(previous =>
        previous.map(simulator =>
          simulator.id === id ? updated : simulator
        )
      );

      setSelected(previous =>
        previous?.id === id ? updated : previous
      );

      setMessage(`"${updated.title}" has been published successfully.`);
    } catch (error) {
      setApiError(
        error instanceof Error
          ? error.message
          : 'Unable to approve this simulator.'
      );
    } finally {
      setApprovingId(null);
    }
  }

  function renderSimulatorCard(simulator: Simulator) {
    return (
      <article className="sim-card" key={simulator.id}>
        <div className={`sim-visual visual-${simulator.subject.toLowerCase().replaceAll(' ', '-')}`}>
          <div className="visual-icon">
            {simulator.subject === 'Physics' ? <Zap /> :
             simulator.subject === 'Chemistry' ? <FlaskConical /> :
             simulator.subject === 'Biology' ? <Activity /> :
             simulator.subject === 'Robotics' ? <Code2 /> : <Sparkles />}
          </div>
          <span className="visual-subject">{simulator.subject}</span>
          <button className="card-more" aria-label="More options">
            <MoreHorizontal />
          </button>
        </div>

        <div className="sim-card-content">
          <div className="card-meta">
            <span>{simulator.level}</span>
            <span className={simulator.status === 'Published' ? 'status-published' : 'status-pending'}>
              {simulator.status}
            </span>
          </div>

          <h3>{simulator.title}</h3>
          <p>{simulator.description}</p>

          <div className="card-footer">
            <span className="creator">
              <Users size={14} /> {simulator.creator}
            </span>
            <button className="text-button" onClick={() => setSelected(simulator)}>
              View details <ArrowUpRight size={15} />
            </button>
          </div>
        </div>
      </article>
    );
  }

  function renderDashboard() {
    return (
      <>
        <section className="welcome-banner">
          <div>
            <span className="eyebrow">
              <Sparkles size={14} /> YOUR STEM INNOVATION SPACE
            </span>
            <h2>Discover. Experiment. Inspire.</h2>
            <p>
              Explore interactive learning experiences and bring AI-powered
              simulations into your classroom.
            </p>
            <button className="primary-button" onClick={() => navigate('Simulator Library')}>
              Explore simulator library <ArrowUpRight size={17} />
            </button>
          </div>

          <div className="banner-art">
            <div className="orbit orbit-one" />
            <div className="orbit orbit-two" />
            <div className="planet"><FlaskConical size={42} /></div>
            <Sparkles className="sparkle sparkle-one" />
            <Zap className="sparkle sparkle-two" />
          </div>
        </section>

        <div className="section-heading">
          <div>
            <h2>Platform overview</h2>
            <p>A snapshot of your simulator ecosystem.</p>
          </div>
          <span className="updated-label">
            <Activity size={14} /> Live database
          </span>
        </div>

        <section className="stats-grid">
          <div className="stat-card">
            <div className="stat-icon blue"><Library /></div>
            <span>Total simulators</span>
            <strong>{simulators.length}</strong>
            <small>Registered in the Hub</small>
          </div>

          <div className="stat-card">
            <div className="stat-icon green"><CheckCircle2 /></div>
            <span>Published</span>
            <strong>{publishedCount}</strong>
            <small>Available in the library</small>
          </div>

          <div className="stat-card">
            <div className="stat-icon orange"><Clock3 /></div>
            <span>Pending review</span>
            <strong>{pendingCount}</strong>
            <small>Awaiting approval</small>
          </div>

          <div className="stat-card">
            <div className="stat-icon purple"><Users /></div>
            <span>Contributors</span>
            <strong>—</strong>
            <small>Available after accounts launch</small>
          </div>
        </section>

        <div className="section-heading">
          <div>
            <h2>Featured simulators</h2>
            <p>Explore some of the learning experiences available.</p>
          </div>
          <button className="text-button" onClick={() => navigate('Simulator Library')}>
            View all <ArrowUpRight size={15} />
          </button>
        </div>

        {publishedSimulators.length > 0 ? (
          <section className="sim-grid">
            {publishedSimulators.slice(0, 3).map(renderSimulatorCard)}
          </section>
        ) : (
          <div className="empty-state">
            <Library size={32} />
            <h3>No published simulators yet</h3>
            <p>Approved simulators will appear here.</p>
          </div>
        )}

        <section className="integration-banner">
          <div className="integration-icon"><GraduationCap /></div>
          <div>
            <h3>Ready to bring simulations into your courses?</h3>
            <p>Connect your simulator library to OpenOLAT using the LTI 1.3 standard.</p>
          </div>
          <button
            className="secondary-button"
            onClick={() => setMessage('LTI integration will be configured in a later development step.')}
          >
            Integration guide <ArrowUpRight size={15} />
          </button>
        </section>
      </>
    );
  }

  function renderLibrary() {
    const visibleSimulators = filtered.filter(
      simulator => simulator.status === 'Published'
    );

    return (
      <>
        <div className="page-intro">
          <span className="eyebrow">EXPLORE AND DISCOVER</span>
          <h2>Simulator Library</h2>
          <p>Find interactive simulations to enrich teaching and learning.</p>
        </div>

        <div className="library-toolbar">
          <div className="search-box">
            <Search size={18} />
            <input
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Search simulators, subjects or topics..."
            />
            {search && (
              <button onClick={() => setSearch('')}>
                <X size={16} />
              </button>
            )}
          </div>

          <div className="filter-box">
            <Filter size={16} />
            <select value={subject} onChange={e => setSubject(e.target.value)}>
              <option>All subjects</option>
              <option>Physics</option>
              <option>Chemistry</option>
              <option>Biology</option>
              <option>Computer Science</option>
              <option>Robotics</option>
              <option>Mathematics</option>
              <option>Engineering</option>
              <option>Other</option>
            </select>
            <ChevronDown size={15} />
          </div>
        </div>

        <div className="results-label">
          {visibleSimulators.length} published simulators found
        </div>

        {visibleSimulators.length > 0 ? (
          <section className="sim-grid">
            {visibleSimulators.map(renderSimulatorCard)}
          </section>
        ) : (
          <div className="empty-state">
            <Search size={32} />
            <h3>No simulators found</h3>
            <p>Try another keyword or subject.</p>
          </div>
        )}
      </>
    );
  }

  function renderSubmit() {
    return (
      <>
        <div className="page-intro">
          <span className="eyebrow">SHARE YOUR INNOVATION</span>
          <h2>Submit a Simulator</h2>
          <p>Contribute an interactive learning experience to the community.</p>
        </div>

        <div className="form-layout">
          <form className="submission-form" onSubmit={submitSimulator}>
            <h3>Simulator information</h3>
            <p className="form-description">
              Provide the details of your simulator. New submissions will be
              reviewed before publication.
            </p>

            <label>Simulator title *</label>
            <input
              required
              value={form.title}
              onChange={e => setForm({ ...form, title: e.target.value })}
              placeholder="e.g. Virtual Physics Laboratory"
            />

            <label>Public simulator URL *</label>
            <input
              required
              type="url"
              value={form.url}
              onChange={e => setForm({ ...form, url: e.target.value })}
              placeholder="https://your-simulator.example.com"
            />
            <small className="field-hint">
              Use a publicly deployed HTTPS URL, not an editor or preview link.
            </small>

            <div className="form-row">
              <div>
                <label>Subject *</label>
                <select
                  value={form.subject}
                  onChange={e => setForm({ ...form, subject: e.target.value })}
                >
                  <option>Physics</option>
                  <option>Chemistry</option>
                  <option>Biology</option>
                  <option>Computer Science</option>
                  <option>Robotics</option>
                  <option>Mathematics</option>
                  <option>Engineering</option>
                  <option>Other</option>
                </select>
              </div>

              <div>
                <label>Education level *</label>
                <select
                  value={form.level}
                  onChange={e => setForm({ ...form, level: e.target.value })}
                >
                  <option>Primary</option>
                  <option>Grades 6–8</option>
                  <option>Grades 9–12</option>
                  <option>Higher Education</option>
                  <option>All levels</option>
                </select>
              </div>
            </div>

            <label>Description *</label>
            <textarea
              required
              rows={4}
              value={form.description}
              onChange={e => setForm({ ...form, description: e.target.value })}
              placeholder="Describe the learning objectives, features and intended use..."
            />

            <button
              type="submit"
              className="primary-button submit-button"
              disabled={submitting}
            >
              {submitting ? 'Submitting...' : 'Submit for review'}
              {!submitting && <ArrowUpRight size={17} />}
            </button>
          </form>

          <aside className="submission-aside">
            <div className="aside-icon"><ShieldCheck /></div>
            <h3>Before you submit</h3>
            <ul>
              <li>Ensure your simulator is publicly accessible over HTTPS.</li>
              <li>Check that the simulator can be embedded in an iframe.</li>
              <li>Make sure you have permission to share the content.</li>
              <li>Include clear learning objectives and instructions.</li>
            </ul>
            <div className="notice-box">
              <CircleHelp size={17} />
              <span>
                Submission does not automatically guarantee LTI compatibility.
                The Hub will perform compatibility checks in a later stage.
              </span>
            </div>
          </aside>
        </div>
      </>
    );
  }

  function renderMySimulators() {
    const mine = simulators.filter(
      simulator => mySimulatorIds.includes(simulator.id)
    );

    return (
      <>
        <div className="page-intro">
          <span className="eyebrow">YOUR CONTRIBUTIONS</span>
          <h2>My Simulators</h2>
          <p>Manage and track the simulators you have submitted in this session.</p>
        </div>

        {message && (
          <div className="success-message">
            <CheckCircle2 size={18} />{message}
          </div>
        )}

        {mine.length === 0 ? (
          <div className="empty-state">
            <FilePlus2 size={36} />
            <h3>No submissions in this session</h3>
            <p>Share your first interactive learning experience with the community.</p>
            <button
              className="primary-button"
              onClick={() => navigate('Submit Simulator')}
            >
              Submit a simulator <Plus size={16} />
            </button>
          </div>
        ) : (
          <section className="sim-grid">
            {mine.map(renderSimulatorCard)}
          </section>
        )}
      </>
    );
  }

  function renderAdministration() {
    const pending = simulators.filter(
      simulator => simulator.status === 'Pending'
    );

    return (
      <>
        <div className="page-intro">
          <span className="eyebrow">PLATFORM MANAGEMENT</span>
          <h2>Administration</h2>
          <p>Review simulator submissions and manage the content library.</p>
        </div>

        <div className="admin-notice">
          <ShieldCheck size={20} />
          <div>
            <strong>Development administration</strong>
            <p>
              Approval actions are now saved through the backend. Authentication
              and role-based permissions are still required before public deployment.
            </p>
          </div>
        </div>

        <h3 className="subheading">Pending submissions ({pending.length})</h3>

        {pending.length === 0 ? (
          <div className="empty-state">
            <CheckCircle2 size={32} />
            <h3>No pending submissions</h3>
          </div>
        ) : (
          <div className="admin-list">
            {pending.map(simulator => (
              <div className="admin-item" key={simulator.id}>
                <div className="admin-item-icon"><FlaskConical /></div>

                <div className="admin-item-info">
                  <strong>{simulator.title}</strong>
                  <span>
                    {simulator.subject} · {simulator.level} · {simulator.creator}
                  </span>
                </div>

                <button
                  className="secondary-button"
                  onClick={() => setSelected(simulator)}
                >
                  Review
                </button>

                <button
                  className="approve-button"
                  disabled={approvingId === simulator.id}
                  onClick={() => void handleApprove(simulator.id)}
                >
                  {approvingId === simulator.id ? 'Approving...' : 'Approve'}
                </button>
              </div>
            ))}
          </div>
        )}
      </>
    );
  }

  function renderDetail() {
    if (!selected) return null;

    return (
      <div className="detail-overlay" onClick={() => setSelected(null)}>
        <div className="detail-panel" onClick={e => e.stopPropagation()}>
          <button className="close-detail" onClick={() => setSelected(null)}>
            <X />
          </button>

          <div className="detail-visual"><FlaskConical size={42} /></div>

          <span className="eyebrow">
            {selected.subject} · {selected.level}
          </span>

          <h2>{selected.title}</h2>
          <p>{selected.description}</p>

          <div className="detail-info">
            <span>Contributor</span>
            <strong>{selected.creator}</strong>
          </div>

          <div className="detail-info">
            <span>Status</span>
            <strong>{selected.status}</strong>
          </div>

          {selected.url && (
            <a
              className="primary-button detail-link"
              href={selected.url}
              target="_blank"
              rel="noreferrer"
            >
              Open original simulator <ExternalLink size={16} />
            </a>
          )}

          <button
            className="secondary-button full-button"
            onClick={() => setMessage('LTI launch links will be generated after backend integration.')}
          >
            Copy LTI launch link <Code2 size={16} />
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="app-shell">
      <aside className={`sidebar ${mobileMenu ? 'sidebar-open' : ''}`}>
        <div className="brand">
          <div className="brand-mark"><Sparkles size={23} /></div>
          <div>
            <strong>AI Simulator<span>Hub</span></strong>
            <small>LEARN · EXPLORE · CREATE</small>
          </div>
          <button
            className="mobile-close"
            onClick={() => setMobileMenu(false)}
          >
            <X />
          </button>
        </div>

        <div className="workspace-label">WORKSPACE</div>

        <nav className="main-nav">
          {navigation.map(item => {
            const Icon = item.icon;

            return (
              <button
                key={item.label}
                className={`nav-item ${page === item.label ? 'active' : ''}`}
                onClick={() => navigate(item.label)}
              >
                <Icon size={19} />
                <span>{item.label}</span>
                {item.label === 'Administration' && (
                  <span className="nav-lock">ADMIN</span>
                )}
              </button>
            );
          })}
        </nav>

        <div className="sidebar-bottom">
          <div className="help-card">
            <div className="help-icon"><LifeBuoy size={18} /></div>
            <strong>Need a hand?</strong>
            <p>Learn how to integrate simulators into your courses.</p>
            <button
              onClick={() => setMessage('Integration documentation will be available in a future release.')}
            >
              View documentation <ArrowUpRight size={14} />
            </button>
          </div>

          <div className="profile">
            <div className="avatar">HN</div>
            <div className="profile-info">
              <strong>Hub Administrator</strong>
              <span>Development account</span>
            </div>
            <MoreHorizontal size={20} />
          </div>
        </div>
      </aside>

      {mobileMenu && (
        <div
          className="mobile-backdrop"
          onClick={() => setMobileMenu(false)}
        />
      )}

      <main className="main-area">
        <header className="topbar">
          <button
            className="mobile-menu-button"
            onClick={() => setMobileMenu(true)}
          >
            <Menu />
          </button>

          <div className="breadcrumb">
            <span>Workspace</span>
            <span>/</span>
            <strong>{selected ? 'Simulator details' : page}</strong>
          </div>

          <div className="topbar-right">
            <span className="environment-label">
              <span /> Local development
            </span>
            <button
              className="icon-button"
              title="Settings"
              onClick={() => setMessage('Settings will be available in a future release.')}
            >
              <Settings size={19} />
            </button>
            <div className="top-avatar">HN</div>
          </div>
        </header>

        <div className="page-content">
          {loading && (
            <div className="info-message">
              Connecting to AI Simulator Hub database...
            </div>
          )}

          {apiError && (
            <div className="info-message">
              Backend error: {apiError}
              <button onClick={() => setApiError('')}>
                <X size={15} />
              </button>
            </div>
          )}

          {message && page !== 'My Simulators' && (
            <div className="info-message">
              {message}
              <button onClick={() => setMessage('')}>
                <X size={15} />
              </button>
            </div>
          )}

          {selected ? renderDetail() :
            page === 'Dashboard' ? renderDashboard() :
            page === 'Simulator Library' ? renderLibrary() :
            page === 'Submit Simulator' ? renderSubmit() :
            page === 'My Simulators' ? renderMySimulators() :
            renderAdministration()}
        </div>

        <footer className="footer">
          <span>© 2026 AI Simulator Hub</span>
          <span>Built for the future of interactive learning</span>
        </footer>
      </main>
    </div>
  );
}

export default App;
