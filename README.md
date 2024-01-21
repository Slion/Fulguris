# Fulguris
[![Discord Invite](https://img.shields.io/discord/828559272752840713?color=%23525dea&label=Chat&logo=discord&logoColor=white)](https://discord.com/invite/7M4Ms5dMZE)

Fulguris is a [web browser] for Android. Through a customizable interface it offers a premium experience on a range of Android, Chromebooks and Windows 11 devices.

# Builds
[![Google Play](https://github.com/Slion/Fulguris/actions/workflows/build-main-fulguris-google-play.yml/badge.svg)](https://github.com/Slion/Fulguris/actions/workflows/build-main-fulguris-google-play.yml)
[![Download](https://github.com/Slion/Fulguris/actions/workflows/build-main-fulguris-download.yml/badge.svg)](https://github.com/Slion/Fulguris/actions/workflows/build-main-fulguris-download.yml)
[![F-Droid](https://github.com/Slion/Fulguris/actions/workflows/build-main-fulguris-fdroid.yml/badge.svg)](https://github.com/Slion/Fulguris/actions/workflows/build-main-fulguris-fdroid.yml)
[![Rebrand](https://github.com/Slion/Fulguris/actions/workflows/build-main-styx.yml/badge.svg)](https://github.com/Slion/Fulguris/actions/workflows/build-main-styx.yml)

# Downloads

[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=net.slions.fulguris.full.playstore)
[<img src="https://slions.net/badge-get-it-on.png"
     alt="Get it on Slions"
     height="80">](http://fulguris.slions.net)
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/net.slions.fulguris.full.fdroid/)

## Release channels
We have three official public release channels linked above and described below. While we strive to produce stable releases, the Fulguris project is a very small operation. Ultimately the community is responsible for doing the testing and validation of releases. All three variants of the app can be installed at the same time on your device. They are effectively three distinct similar apps. You can differenciate them in your launcher from their slightly modified icon design. Which versions you use and how you manage your updates will define your experience with Fulguris. Update often to help catch bugs before they reach too many users. Wait a couple of weeks and check for known issues before updating if you want to have fewer surprises and a more stable experience.

### 🟢 Google Play

Considered the most stable release channel. Optional [Firebase] statistics and crash report.
If you have more than twenty tabs in a session, it will gently remind you to go for the low cost yearly subscription, every time you open a new tab.

### 🟡 Download 

Provides release candidates and stable releases. Optional [Firebase] statistics and crash report.
Unlimited tabs without subscription. From here you get the most up to date versions.

### 🟠 F-Droid

Provides release candidates and stable releases. No [Firebase] statistics and crash report. Unlimited tabs without subscription.
We have no direct control of the release schedule on this channel. If a broken release reaches it, it may take a couple of weeks to fix it.


# Contribute

## Test

The best way to contribute is to use Fulguris. There is no beta test program yet. However releases tend to be published more often on [Slions.net] and it is easy to switch back to an [earlier version] if needed.
Please report issues on [GitHub](https://github.com/slion/fulguris/issues).

## Rate

Give us ⭐⭐⭐⭐⭐ on [Google Play].  
If you don't think it is worth five stars yet then just [open an issue](https://github.com/slion/fulguris/issues) instead. 

## Engage

You can engage with us on [our forums], on [GitHub](https://github.com/slion/fulguris/issues), on [Discord] or any other social media you can track us down to.

## Translate

Please go ahead and translate on [CrowdIn](https://crowdin.com/project/fulguris-web-browser).

## Code
Code contributions are of course welcome, both bug fix and features.
Please understand that code quality, design and architecture are extremely important as the foremost priority of this project is to deliver a premium browsing experience.
We do our best to avoid treating users as beta testers. Therefore if you want to deliver a new feature expect it to take several iterations before it reaches maturity and is ready for production.

## Finance

Please consider [sponsoring us on GitHub](https://github.com/sponsors/Slion) or through in-apps on [Google Play].
The ability of this project to finance itself is what will ultimately determine its long term success.

# Features
📑Sessions  
🌍 Address bar  
🚦Vertical tab panel  
🚥Horizontal tab bar  
⚙Tabs management  
🏞Screen orientations  
🔖Bookmarks  
⌚History  
🌗Force dark mode  
🎨Themes for app and pages  
⛔Ad blocker  
🔒Privacy – Incognito mode  
🔎Search & suggestions – Google, Bing, Yahoo, StartPage, DuckDuckGo…  
♿Accessibility  
⌨Keyboard support  
⚡Hardware accelerated  
🖥️ Desktop rendering  
🖥️ Android desktop support – Samsung Dex, EMUI Desktop  
🌐 Languages  
🔧Settings – lots  
📶 Orbot Proxy support and I2P support – Beta

# Permissions

## Automatically granted
* `INTERNET`: necessary to access the internet.
* `ACCESS_NETWORK_STATE`: used by the browser to stop loading resources when network access is lost.
* `INSTALL_SHORTCUT`: used to add shortcuts with the "Add to home screen" option.

## Requested only when needed
* `WRITE_EXTERNAL_STORAGE`: needed to download files and export bookmarks.
* `READ_EXTERNAL_STORAGE`: needed to download files and import bookmarks.
* `ACCESS_FINE_LOCATION`: needed for sites like Google Maps, requires "Location access" option to be enabled (default disabled).
* `RECORD_AUDIO`: needed to support WebRTC, requires "WebRTC Support" option to be enabled (default disabled).
* `CAMERA`: needed to support WebRTC, requires "WebRTC Support" option to be enabled (default disabled).
* `MODIFY_AUDIO_SETTINGS`: needed to support WebRTC, requires "WebRTC Support" option to be enabled (default disabled).

# Tools

Here are some of the tools we use to develop Fulguris:

* [Android Studio]
* [SVG path editor]: Most useful to quickly modify icons
* [Affinity Designer]: Vector graphics


[slions.net]: https://slions.net/resources/fulguris.10/
[earlier version]: https://slions.net/resources/fulguris.10/history
[Google Play]: https://play.google.com/store/apps/details?id=net.slions.fulguris.full.playstore
[our forums]: https://slions.net
[Discord]: https://discord.com/invite/7M4Ms5dMZE
[Web browser]: https://en.wikipedia.org/wiki/Web_browser
[Firebase]: https://firebase.google.com
[SVG path editor]: https://yqnn.github.io/svg-path-editor/
[Android Studio]: https://developer.android.com/studio
[Affinity Designer]: https://affinity.serif.com/en-us/designer/
