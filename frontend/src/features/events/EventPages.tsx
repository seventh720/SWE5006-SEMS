import { type FormEvent, useEffect, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { eventListPath, formatEventTime, readEventQuery, type EventPage, type PublishedEvent } from "./events";
import { useEventRequest } from "./useEventRequest";

function EventNavigation() {
  const { user } = useAuth();
  return <nav className="event-nav" aria-label="Event navigation">
    <Link className="text-link" to="/events">SEMS · Explore events</Link>
    <Link className="text-link" to={user ? "/" : "/login"}>{user ? "My dashboard" : "Login / Register"}</Link>
  </nav>;
}

function RequestState({ loading, error, retry }: { loading: boolean; error?: string; retry: () => void }) {
  if (loading) return <div className="card event-feedback" role="status">Loading events…</div>;
  if (error) return <div className="card event-feedback">
    <p className="error-message" role="alert">{error}</p>
    <button className="secondary-button" onClick={retry}>Try again</button>
  </div>;
  return null;
}

export function EventListPage() {
  const [params, setParams] = useSearchParams();
  const { page, keyword } = readEventQuery(params);
  const [search, setSearch] = useState(keyword);
  const request = useEventRequest<EventPage>(eventListPath(page, keyword));
  useEffect(() => setSearch(keyword), [keyword]);

  function navigate(nextPage: number, nextKeyword = keyword) {
    setParams({ ...(nextKeyword ? { keyword: nextKeyword } : {}), page: String(nextPage) });
  }
  function submit(event: FormEvent) {
    event.preventDefault();
    navigate(0, search.trim());
  }

  return <main className="app-shell events-shell">
    <EventNavigation />
    <header className="event-hero">
      <p className="eyebrow">Discover · Connect · Experience</p>
      <h1>Find your next event</h1>
      <p>Explore upcoming experiences and find a reason to come together.</p>
    </header>
    <form className="card event-search" onSubmit={submit} role="search">
      <label htmlFor="event-keyword">Search by event title
        <input id="event-keyword" type="search" placeholder="What would you like to explore?" value={search} onChange={(event) => setSearch(event.target.value)} />
      </label>
      <button className="primary-button compact" type="submit">Search events</button>
      {keyword && <button className="secondary-button" type="button" onClick={() => { setSearch(""); navigate(0, ""); }}>Clear search</button>}
    </form>
    <section aria-label="Published events" aria-busy={request.loading}>
      <div className="event-results-heading"><h2>{keyword ? `Results for “${keyword}”` : "Published events"}</h2>
        {request.data && <span className="muted" role="status">{request.data.totalElements} events</span>}
      </div>
      <RequestState {...request} />
      {request.data && <>
        {request.data.items.length === 0 ? <div className="card event-feedback">
          <h2>{page > 0 ? "No events on this page" : keyword ? "No matching events" : "More experiences are on the way"}</h2>
          <p>{keyword ? "Try a different title or clear your search." : "Check back soon for published events."}</p>
          {page > 0 && <button className="secondary-button" onClick={() => navigate(0)}>Back to first page</button>}
        </div> : <div className="event-grid">{request.data.items.map((item) => <article className="card event-card" key={item.id}>
          <p className="section-label">{formatEventTime(item.startsAt)} · SGT</p>
          <h2><Link to={`/events/${encodeURIComponent(item.id)}?${params}`} className="text-link">{item.title}</Link></h2>
          <p className="event-location">{item.location}</p>
          <p className="event-excerpt">{item.description}</p>
          <div className="event-card-footer"><span className="muted">Capacity: {item.capacity}</span><Link className="text-link" to={`/events/${encodeURIComponent(item.id)}?${params}`}>View details →</Link></div>
        </article>)}</div>}
        {request.data.items.length > 0 && request.data.totalPages > 0 && <nav className="event-pagination" aria-label="Event pages">
          <button className="secondary-button" disabled={page === 0} onClick={() => navigate(page - 1)}>Previous</button>
          <span>Page {page + 1} of {request.data.totalPages}</span>
          <button className="secondary-button" disabled={page + 1 >= request.data.totalPages} onClick={() => navigate(page + 1)}>Next</button>
        </nav>}
      </>}
    </section>
  </main>;
}

export function EventDetailPage() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const request = useEventRequest<PublishedEvent>(`/api/v1/events/${encodeURIComponent(id ?? "")}`, true);
  const item = request.data;
  return <main className="app-shell events-shell">
    <EventNavigation />
    <Link className="text-link" to={`/events?${params}`}>← Back to events</Link>
    <RequestState {...request} />
    {item && <>
      <header className="event-hero"><p className="eyebrow">Published event</p><h1>{item.title}</h1><p>{item.location}</p></header>
      <div className="event-detail-grid">
        <section className="card"><h2>About this event</h2><p className="event-description">{item.description}</p></section>
        <aside className="card"><h2>Event information</h2><dl className="event-facts">
          <dt>Starts</dt><dd><time dateTime={item.startsAt}>{formatEventTime(item.startsAt)}</time></dd>
          <dt>Ends</dt><dd><time dateTime={item.endsAt}>{formatEventTime(item.endsAt)}</time></dd>
          <dt>Time zone</dt><dd>Singapore (SGT, UTC+08:00)</dd>
          <dt>Location</dt><dd>{item.location}</dd>
          <dt>Capacity</dt><dd>{item.capacity} attendees</dd>
        </dl><p className="muted">Capacity is the event size, not ticket availability.</p></aside>
      </div>
    </>}
  </main>;
}
