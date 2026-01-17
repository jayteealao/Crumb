# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
