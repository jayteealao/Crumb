# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Twitter bookmark cards now render tweet images, including a 2×2 grid for
  multi-image tweets, with a full-screen zoomable image viewer on tap
- Inline video playback on bookmark cards, supporting adaptive streams (HLS/DASH)
  as well as progressive video, driven by a single shared player to keep memory
  stable while scrolling
- Link previews on bookmark cards (title and image when available); tapping the
  preview opens the link in the browser, tapping the card opens the tweet
- Quoted tweets now render on bookmark cards (author and text), with an
  "unavailable" placeholder when the quoted tweet has been deleted
- Saved-count header and a real per-card number, replacing the `000` placeholders
- Working type filters for the Twitter feed (images, video, links, articles,
  threads, text)
- Account deletion, which removes your stored data and disconnects linked accounts
- See [Understanding how Twitter bookmark cards get their data](docs/twitter-bookmark-rendering.md)
  for the design rationale behind these changes

### Fixed
- The bookmark time label now reflects when a tweet was saved rather than when it
  was originally posted, falls back to the post date, and shows a neutral marker
  when no date is available instead of a fabricated time
- The feed is now ordered most-recently-saved first (undated items last) instead of
  by a client sequence that could drift between syncs
- Transient sync errors now retry instead of being silently dropped, and outbound
  network calls during sync are bounded by timeouts
- Missing media on a card now degrades to text and re-fetches on the next view, and
  older saved tweets are repaired by a one-time backfill

### Changed
- Locally stored authentication tokens are now encrypted at rest
- Updated the media-playback and image-loading libraries
- Hardened link-preview fetching to safe public destinations only, added single-use
  protection to the sign-in flow, and required authentication on internal endpoints
- Removed a vulnerable transitive dependency by updating the preferences library

## [1.1] - 2026-01-17

### Added
- Reddit feature module with OAuth authentication
- Core models module for shared data classes
- Crumbs design system with custom theme, typography, and components
- CrumbsTopBar component with collapsible search
- CrumbsBottomNav component with cut-corner indicator
- CrumbsCard and CrumbsButton components
- Roborazzi screenshot tests for design system components
- Funnel Display custom font family
- Reddit OAuth client with PKCE flow
- Unified OAuth redirect URI for Twitter and Reddit

### Fixed
- Compose stability crash caused by missing compiler plugin in core/models module
- Reddit OAuth redirect URI configuration with callback path
- OAuth endpoint changed from compact to regular authorize endpoint
- Font loading strategy changed to async to prevent crashes
- Bottom navigation selection indicator styling

### Changed
- Design system updated to Modern Minimal aesthetic
- Typography scale using Funnel Display font
- Color tokens updated for light and dark themes
- Shape system with cut-corner design elements
- OAuth redirect URIs unified to `crumbs://graphitenerd.xyz/callback`

## [1.0] - 2026-01-01

### Added
- Initial release
- Twitter bookmarks integration
- Basic UI with Material Design 3
