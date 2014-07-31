# Mitro Password Manager

Mitro saves all your passwords, synchronizes them across all your devices, and lets you share them with others. It has extensions for Chrome, Firefox, and Safari, mobile apps for iOS and Android, and a server to perform the synchronization.

If you are user, install it from the [Mitro web site](https://www.mitro.co/). If you have any questions, you can ask on the [mitro-dev@googlegroups.com](https://groups.google.com/forum/#!forum/mitro-dev) mailing list, or [send @MitroCo a tweet](https://www.twitter.com/MitroCo).


## Quick Start

1. Install dependencies (see [`browser-ext/README`](browser-ext/README.md), [`mitro-core/README`](browser-ext/README.md)) [node, npm, homebrew, java, ant]
2. Clone repository: `git clone https://github.com/mitro-co/mitro`
3. Install browser extension dependencies:

    ```
cd mitro
cd browser-ext/api
./build.sh
cd -
```
4. Run the regression tests to ensure your source tree works:

    ```
cd browser-ext/api/js/cli
./runtests.sh FAST && echo "SUCCESS"
```
5. Look for "SUCCESS" on the last line.
6. Build the browser extension:

    ```
cd -
cd browser-ext/login
make release
```
7. To build Firefox, use `make firefox` and to build Safari use `make safari`.

The extensions will be in the `browser-ext/login/build` directory.
