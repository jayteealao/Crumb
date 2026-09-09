# Understanding how Twitter bookmark cards get their data

This page explains *why* the Twitter bookmark feed is built the way it is — where
each card's timestamp, sort position, media, quoted tweet, and link preview come
from, and the design choices behind them. It is background and rationale, not a
setup guide. If you just want to build and run the app, start with the
[README](../README.md).

## Why this matters

The Twitter bookmark feed looks simple — a scrolling list of cards — but almost
every visible field on a card is the result of a non-obvious decision forced by a
limitation of the upstream X API or of local storage. Knowing those reasons makes
the code legible: it explains why a timestamp is stamped on the server, why the
feed sort has a fallback, why quoted tweets are stored without foreign keys, and
why link previews are produced by a separate background process rather than during
the sync poll.

## The core problem: X gives us less than the card shows

A bookmark card wants to show *when you saved a tweet*, *what media it contains*,
*the tweet it quotes*, and *a preview of any link it points to*. The X v2 bookmarks
API returns almost none of that directly:

- It does **not** return a "bookmarked at" time. The only timestamp is the tweet's
  own creation date, which is usually the wrong thing to show — a tweet created
  months ago may have been bookmarked yesterday.
- It returns media and referenced-tweet data only as cross-references that have to
  be re-joined, and earlier versions of the sync silently dropped that join.
- It does not return rich link metadata (title, image) on the plans we run against.

So the feature is, in large part, about *manufacturing* the data the card needs and
storing it reliably — across an upstream API, a Cloud Function, Firestore, and a
local Room database — rather than about UI formatting.

## Timestamps: a server-stamped "first seen" time, with a fallback

Because X has no bookmark timestamp, the sync poll stamps each tweet with the time
the server *first saw* it (`retrievedAt`). This is an honest approximation of
recency: it is not the true bookmark moment (which is unknowable), but it orders the
feed the way a user expects — most recently encountered first.

The card's relative-time label ("2d ago") is derived from `retrievedAt` when
present, and falls back to the tweet's own creation date otherwise. When neither
can be parsed, the card shows a `_` marker rather than inventing a "now" — a
fabricated timestamp is worse than an honest blank, because it silently lies about
recency.

The feed sorts by `retrievedAt` descending, then creation date descending, with
rows that have no `retrievedAt` sorted **last**. This last point is subtle: SQLite
orders NULLs *first* under a descending sort, which would float undated rows to the
top — the opposite of what we want — so the query explicitly pushes them to the
bottom. A composite index backs the sort so the feed never does a full-table scan.

There is a second wrinkle: historical tweets carried creation dates in more than one
string format. Rather than teach the client every legacy format, the server
normalizes new dates to a single canonical form and the client parses defensively,
so a mixed corpus during rollout still sorts and labels correctly.

## Media: render what we have, re-fetch what we are missing, never block the feed

Images, video, and link previews all render inside one shared media container so the
three surfaces share layout, theming, and loading behavior rather than each
re-inventing it. The guiding principle is graceful degradation: a card with missing
media settles to text rather than showing a broken frame, and attempts a re-fetch
the next time it scrolls into view. A legacy backfill pass repairs older saved
tweets that predate media persistence.

Inline video is the riskiest surface. A naive implementation allocates one video
player per card, which leaks memory and stutters in a long list. Instead the feed
keeps a **single** shared player that is attached to whichever card is active and
detached on recycle, and playback pauses with the app lifecycle. This is why the
video code looks more elaborate than "just play the URL" — the complexity buys
stable memory across dozens of video cards. Supporting modern adaptive streams
(HLS/DASH), not only progressive MP4, required moving the media stack forward
several versions.

## Quoted tweets: full restoration, deliberately without foreign keys

Quoted-tweet data used to be discarded during sync. The reason was a real
production failure: the original relational model linked each tweet to *every*
referenced entity — including mention and reply users that the sync never actually
fetched — so those links pointed at rows that did not exist, and the database
rejected the writes, rolling back the whole batch.

The fix restores quoted tweets end-to-end but rebuilds the relationship on a
**foreign-key-free** junction keyed by the parent tweet. The bodies of quoted
tweets are stored as ordinary tweet records and matched by reference id, so there is
no foreign key left to violate. This is a case where the obvious relational design
was the source of the bug, and removing referential enforcement — usually a smell —
is the correct, safer choice given how partial the upstream data is. A deleted or
unavailable quoted tweet renders an "unavailable" placeholder, mirroring how X
itself omits the body without raising an error.

## Link previews: enrichment belongs in the background, not the poll

A link preview needs a title and image that X does not reliably provide. Fetching
that metadata means making an outbound HTTP request per link, which is slow and
failure-prone — exactly the kind of work that should not sit inside the time-bounded
sync poll, where it would risk the whole sync timing out.

So enrichment runs as a **separate**, Firestore-triggered background function that
reads OpenGraph metadata after a tweet is written. It is best-effort: the link URL
is always stored, while the title and image are optional, so a card always has at
least a tappable URL chip even when enrichment fails. Tapping the preview opens the
external link; tapping the rest of the card opens the tweet. Because enrichment
reaches arbitrary outbound URLs, it is constrained to safe public destinations and
refuses redirects, so a malicious link cannot steer it at internal infrastructure.

## How the pieces relate

The same data flows through every surface: the **sync poll** writes tweets (with
`retrievedAt`, media references, and quoted-tweet bodies) to Firestore; a separate
**enrichment function** adds link metadata; the app reads Firestore into **Room**
through a join that re-attaches media and referenced tweets; the **feed query**
sorts and counts; and the **card** renders whatever data survived that journey,
degrading gracefully wherever a piece is missing. Schema changes to Room are
versioned sequentially so an upgrade never crashes on an older database.

## Trade-offs and things to keep in mind

- `retrievedAt` is *first-seen* time, not true bookmark time. We accept a small
  inaccuracy in exchange for a recency signal that exists at all.
- The per-card number is the database row id. It is stable in normal use but is not
  guaranteed to survive a database compaction — a deliberate, accepted trade for not
  maintaining a separate ordinal.
- Type filters that depend on media or link data under-match until the corresponding
  backfill has populated those fields; they self-correct as data lands.
- A few heavier items remain intentionally deferred (see the changelog's follow-up
  notes): an incremental redesign of the pending-delete diff to avoid a
  full-collection read, full-file encryption of the local token store, and deeper
  batching of link enrichment.

## Further reading

- [README](../README.md) — build, test, and run the app.
- [CHANGELOG](../CHANGELOG.md) — the user-visible changes this work delivered.
