<h1 align="center">Moonfin for Android TV</h1>
<h3 align="center">Enhanced Jellyfin client for Android TV, Nvidia Shield, and Amazon Fire TV devices</h3>

---

<p align="center">
   <img width="4305" height="2659" alt="splash-background" src="https://github.com/user-attachments/assets/8618363e-d982-4828-8274-a2c3c7623ddb" />
</p>

[![License](https://img.shields.io/github/license/Moonfin-Client/AndroidTV-FireTV.svg)](https://github.com/Moonfin-Client/AndroidTV-FireTV)
[![Release](https://img.shields.io/github/release/Moonfin-Client/AndroidTV-FireTV.svg)](https://github.com/Moonfin-Client/AndroidTV-FireTV/releases)

> **[← Back to main Moonfin project](https://github.com/Moonfin-Client)**

Moonfin for Android TV is an enhanced fork of the official Jellyfin Android TV client, optimized for the viewing experience on Android TV, Nvidia Shield, and Amazon Fire TV devices.

## Features & Enhancements

Moonfin for Android TV builds on the solid foundation of Jellyfin with targeted improvements for TV viewing:

### 🎬 Featured Media Bar
- Rotating showcase of 15 random movies and TV shows right on your home screen
- See ratings, genres, runtime, and a quick overview without extra clicks
- Smooth crossfade transitions as items change, with matching backdrop images
- Height and positioning tuned for viewing from the couch

### 📊 Enhanced Details Screen
- Metadata organized into clear sections: genres, directors, writers, studios, and runtime
- Taglines displayed above the description where available
- Cast photos appear as circles for a cleaner look
- Fits more useful information on screen without feeling cramped

### 🏠 Improved Home Screen
- Item details show up right in the row, no need to open every title to see what it is
- Content is laid out more clearly so it's easier to find what you want
- Buttons look better when not focused (transparent instead of distracting)

### 🎨 UI Polish
- Tweaked colors, spacing, and text to look more modern
- Icons and visual elements are consistent throughout
- Better contrast makes text easier to read
- Transitions and animations feel responsive

## Screenshots

<img width="1920" height="1080" alt="Screenshot_20251120_191359" src="https://github.com/user-attachments/assets/8c356be0-49f1-4113-ab7a-e4e90dee812f" />

<img width="1920" height="1080" alt="Screenshot_20251120_191506" src="https://github.com/user-attachments/assets/f27f90d9-8e74-479f-a632-0fd57e31ae3e" />

<img width="1920" height="1080" alt="Screenshot_20251120_191533" src="https://github.com/user-attachments/assets/02fdbb8f-a109-4bcd-81b6-fe0c3a9bf3e9" />

<img width="1920" height="1080" alt="Screenshot_20251120_191600" src="https://github.com/user-attachments/assets/49eefda8-a882-4371-8b4f-531d07ed7c94" />

---

**Disclaimer:** Screenshots shown in this documentation feature media content, artwork, and actor likenesses for demonstration purposes only. None of the media, studios, actors, or other content depicted are affiliated with, sponsored by, or endorsing the Moonfin client or the Jellyfin project. All rights to the portrayed content belong to their respective copyright holders. These screenshots are used solely to demonstrate the functionality and interface of the application.

---

## Installation

### Pre-built Releases
Download the latest APK from the [Releases page](https://github.com/Moonfin-Client/AndroidTV-FireTV/releases).

**Supported Devices:**
- Android TV devices (Android 5.0+)
- Nvidia Shield TV
- Amazon Fire TV / Fire TV Stick
- Google TV (Chromecast with Google TV)

### Sideloading Instructions
1. Enable "Unknown Sources" or "Install Unknown Apps" in your device settings
2. Transfer the APK to your device or download it directly
3. Use a file manager app to install the APK

## Building from Source

### Prerequisites
- Android Studio Arctic Fox or newer
- JDK 11 or newer
- Android SDK with API 21+ installed

### Steps

1. **Clone the repository:**
```bash
git clone https://github.com/Moonfin-Client/AndroidTV-FireTV.git
cd AndroidTV-FireTV
```

2. **Build debug version:**
```bash
./gradlew assembleDebug
```

3. **Install to connected device:**
```bash
./gradlew installDebug
```

4. **Build release version:**

First, create a `keystore.properties` file in the root directory (use `keystore.properties.template` as a guide):
```properties
storeFile=/path/to/your/keystore.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

Then build:
```bash
./gradlew assembleRelease
```

The APK will be in `app/build/outputs/apk/release/`

## Development

### Developer Notes
- Uses Gradle wrapper (no need to install Gradle separately)
- Android Studio is recommended for development
- Keep Android SDK and build tools updated
- Code style follows upstream Jellyfin conventions
- UI changes should be tested on actual TV devices when possible

## Contributing

We welcome contributions to Moonfin for Android TV!

### Guidelines
1. **Check existing issues** - See if your idea/bug is already reported
2. **Discuss major changes** - Open an issue first for significant features
3. **Follow code style** - Match the existing codebase conventions
4. **Test on TV devices** - Verify changes work on actual Android TV hardware
5. **Consider upstream** - Features that benefit all users should go to Jellyfin first!

### Pull Request Process
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes with clear commit messages
4. Test thoroughly on Android TV devices
5. Submit a pull request with a detailed description

## Translating

Translations are maintained through the Jellyfin Weblate instance:
- [Jellyfin Android TV on Weblate](https://translate.jellyfin.org/projects/jellyfin-android/jellyfin-androidtv)

Translations contributed to Moonfin that are universally applicable will be submitted upstream to benefit the entire community.

## Support & Community

- **Issues** - [GitHub Issues](https://github.com/Moonfin-Client/AndroidTV-FireTV/issues) for bugs and feature requests
- **Discussions** - [GitHub Discussions](https://github.com/Moonfin-Client/AndroidTV-FireTV/discussions) for questions and ideas
- **Upstream Jellyfin** - [jellyfin.org](https://jellyfin.org) for server-related questions

## Credits

Moonfin for Android TV is built upon the excellent work of:

- **[Jellyfin Project](https://jellyfin.org)** - The foundation and upstream codebase
- **[MakD](https://github.com/MakD)** - Original Jellyfin-Media-Bar concept that inspired our featured media bar
- **Jellyfin Android TV Contributors** - All the developers who built the original client
- **Moonfin Contributors** - Everyone who has contributed to this fork

## License

This project inherits the GPL v2 license from the upstream Jellyfin Android TV project. See the [LICENSE](LICENSE) file for details.

---

<p align="center">
   <strong>Moonfin for Android TV</strong> is an independent fork and is not affiliated with the Jellyfin project.<br>
   <a href="https://github.com/Moonfin-Client">← Back to main Moonfin project</a>
</p>
