TODO List:

- [ ] Fix app icon
- [x] Fix package / application id
- [x] Fix errors with doki-exts
- [x] Fix in-app update (disabled)
- [ ] Remake app resources
- [x] README + Documents
- [ ] Change kotatsu:// to doki:// for all translations
- [ ] Fix URL handle
- [ ] Fix toolbar in Reader

## New Features

### Logcat Viewer
- Added a Logcat Viewer in Settings for debugging purposes
- Features filtering by log level (Verbose, Debug, Info, Warn, Error, Assert)
- Includes save to file, copy to clipboard, and clear functions
- Uses color coding for different log levels
- Requires storage permission for save functionality

### SOCKS Proxy Stability Fixes
- Improved timeout handling to prevent crashes during proxy testing
- Added proper exception handling specific to SOCKS proxy connection issues
- Better timeout values configured for different proxy types

### ACRA Crash Reporting Configuration
- Configured ACRA crash reporting endpoints in BaseApp
- Set up authentication and custom data collection
- Added proper crash dialog configuration
