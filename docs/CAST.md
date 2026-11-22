# Google Cast

Cast is optional. Core audiobook import, playback and statistics continue to work offline.

## Use

1. Connect the phone and receiver to the same Wi-Fi network, then open the Cast control in the player or Settings.
2. Choose a receiver in the Auralis device sheet. Auralis uses the Google Cast default media receiver.
3. The Cast menu exposes receiver volume when the selected device reports variable remote-volume support. Otherwise, adjust volume on the receiver itself.

The phone must remain powered on and connected to the local network because it serves audiobook bytes to the receiver. Receiver format support varies. Auralis sends byte-range requests from a token-protected local server; it does not upload audiobook files to a cloud service.

## Current limitation

Chapter rollover, seeking and transcript synchronization need additional live testing across receiver models and long sessions. A known issue can leave the app's chapter or transcript display behind while a Cast receiver continues playing. The code includes chapter-aware queue handling, but that behavior is not considered fully verified until tested on the target speaker.

Cast discovery and playback require Google Play services and local network access. Google Cast SDK diagnostics may be sent by Google after the user opens Cast; the SDK is not initialized for normal offline listening.
