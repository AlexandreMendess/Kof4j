# kfvm — Kof Version Manager

Install, switch between and remove versions of the [Kof](https://github.com/KofLang/Kof4j) toolchain from the command line, on Linux, macOS and Windows.

```console
$ kfvm i lst        # install the latest stable release
$ kfvm i 0.4.10     # install a specific version
$ kfvm u 0.4.10     # make it the active one, or install if its not yet
$ kof version
```

> kfvm is an independent community tool. It is not maintained by or affiliated with the KofLang project. It downloads the official Kof releases published on [GitHub Releases](https://github.com/KofLang/Kof4j/releases).

## Why

Kof ships as a self-contained distribution (compiler, CLI, runtime, stdlib and an embedded JDK), and each release lives in its own folder. Switching between them by hand means downloading tarballs, extracting them side by side and repointing your `PATH`. kfvm does that for you, which is handy when you need to:

- check whether a bug reproduces across releases before reporting it;
- try a nightly pre-release without losing your stable setup;
- go back to an older version when an upgrade breaks something.

## Installation

### Linux and macOS

```bash
curl -fsSL https://raw.githubusercontent.com/etieppo/kfvm/master/install.sh | sh
```

The script builds kfvm from source. If no Kof 0.5.0 or newer is found, it first installs one into `~/.local/share/kof`. When Kof can compile kfvm into a native binary, that binary is installed as `~/.local/bin/kfvm`. Otherwise kfvm is installed as `~/.local/share/kfvm/kfvm.jar` plus a launcher in `~/.local/bin/kfvm`, which runs on the embedded JDK of an installed Kof version. If `~/.local/bin` is not in your `PATH`, the script adds it to your shell config.

### Windows

In PowerShell:

```powershell
irm https://raw.githubusercontent.com/etieppo/kfvm/master/install.ps1 | iex
```

Or from `cmd`:

```bat
powershell -c "irm https://raw.githubusercontent.com/etieppo/kfvm/master/install.ps1 | iex"
```

Requires Windows 10 (1803 or newer) or Windows 11, which ship `curl.exe` and `tar.exe`. Kof publishes Windows builds for x86_64 only.

`install.ps1` does the same as `install.sh`, with the same layout under `%USERPROFILE%`: Kof versions go to `%USERPROFILE%\.local\share\kof`, kfvm is installed as `%USERPROFILE%\.local\share\kfvm\kfvm.jar` plus the launcher `%USERPROFILE%\.local\bin\kfvm.cmd`, and `%USERPROFILE%\.local\bin` is added to your user `PATH`. Open a new terminal after installing.

To call `kof` directly, add `%USERPROFILE%\.local\share\kof\current\bin` to your `PATH` as well.

### Install manually

```bash
git clone https://github.com/etieppo/kfvm
cd kfvm
KFVM_SOURCE=. sh install.sh
```

On Windows:

```powershell
git clone https://github.com/etieppo/kfvm
cd kfvm
$env:KFVM_SOURCE = '.'; powershell -ExecutionPolicy Bypass -File install.ps1
```

## Usage

```bash
kfvm -h
```

```
kfvm ls,  list [-r, --remote]          list installed versions (or available ones)
kfvm i,   install <ver|lst|nightly>    install a version
kfvm u,   use <ver|lst|nightly>        switch the active version or install it
kfvm uni, uninstall <ver|lst|nightly>  remove an installed version
```

Every command has a short alias, so `kfvm install 0.4.10` and `kfvm i 0.4.10` are the same thing.

### Listing versions

```bash
kfvm ls           # versions installed on this machine
kfvm ls -r        # versions available on GitHub Releases
```

### Installing

```bash
kfvm i lst        # latest stable release
kfvm i nightly    # latest pre-release
kfvm i 0.4.10     # a specific version
```

### Switching

```bash
kfvm u 0.4.10
kof version
```

### Removing

```bash
kfvm uni 0.4.10
```

On Windows, kfvm runs on the JDK of the active Kof version and files in use cannot be deleted, so switch to another version (`kfvm u <ver>`) before removing the active one.

## Version specifiers

| Specifier | Meaning | Example |
|---|---|---|
| `<ver>` | An exact version. A prefix is enough when it is unambiguous. | `0.4.10-beta`, or just `0.4.10` |
| `lst` | The latest stable release. | |
| `nightly` | The latest pre-release. | `0.5.0-beta+2026.09.25` |

## How it works

Each version is extracted into its own folder under the install directory, and a `current` symlink points to the active one:

```
~/.local/share/kof/
├── kof-0.4.10-beta-macos-arm64/
├── kof-0.5.0-beta-macos-arm64/
└── current -> kof-0.5.0-beta-macos-arm64/
```

`kfvm use` only moves the `current` link. Nothing is copied or rebuilt, so switching is instant, and each version keeps its own embedded JDK and standard library.

This is the same layout used by Kof's official installer (`scripts/install.sh`), so kfvm can manage versions that were installed with it, and vice versa.

On Windows the layout lives under `%USERPROFILE%\.local\share\kof`, versions end in `-windows-x86_64`, and `current` is a directory junction, which needs neither administrator rights nor Developer Mode. The `kof` launcher of each version is `bin\kof.bat`.

## Running a specific version without switching

Every installed version can be called directly by its path, which is useful for comparing behavior between releases:

```bash
~/.local/share/kof/kof-0.4.10-beta-macos-arm64/bin/kof fmt main.kf | diff main.kf -
~/.local/share/kof/kof-0.5.0-beta-macos-arm64/bin/kof fmt main.kf | diff main.kf -
```

## Uninstalling kfvm

Remove the Kof versions you no longer need with `kfvm uni`, then delete `~/.local/bin/kfvm`, `~/.local/share/kfvm` and the `PATH` line from your shell config. To remove every Kof version as well, delete the install directory:

```bash
rm -rf ~/.local/share/kof
```

On Windows, delete `%USERPROFILE%\.local\bin\kfvm.cmd` and `%USERPROFILE%\.local\share\kfvm`, and remove `%USERPROFILE%\.local\bin` from your user `PATH`. To remove every Kof version as well:

```powershell
Remove-Item -Recurse -Force "$env:USERPROFILE\.local\share\kof"
```
