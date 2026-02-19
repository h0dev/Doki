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
- Added a Logcat Viewer integrated into Debug Settings for debugging purposes
- Features filtering by log level (All, Verbose, Debug, Info, Warn, Error, Assert) via a spinner
- Includes save to file, copy to clipboard, and clear functions as inline buttons
- Uses color coding for different log levels
- Always visible on the Debug tab with filter controls at the top
- Requires storage permission for save functionality
- Added source testing functionality to test all manga sources
- Added log limit options (100, 200, 500, 1000, or All) to prevent UI hanging with large log volumes
- Fixed UI positioning and performance issues for better user experience

### SOCKS Proxy Stability Fixes
- Improved timeout handling to prevent crashes during proxy testing
- Added proper exception handling specific to SOCKS proxy connection issues
- Better timeout values configured for different proxy types

### Crash Reporting Configuration
- Replaced ACRA crash reporting with user-controlled error handler
- Users can now copy error details to clipboard instead of auto-sending to server
- Maintains all existing app functionality while giving users control over error reporting
