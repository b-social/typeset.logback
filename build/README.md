# Common development actions

> [!TIP]
> Install all external tools automatically with [`asdf`](https://asdf-vm.com/) or [`mise`](https://mise.jdx.dev/).  All tool versions are listed in the `.tool-versions` file.

## Lint/format/etc.

```shell
bb lint      # Lint
bb fmt       # Fix formatting
bb outdated  # List outdated deps
```

## Test

Run tests by executing the following commands.

```shell
bb build compile
bb test
```

## Build

> [!WARNING]
> If you have any local changes that haven't been committed, they will be included in the built jar file.

Build and install the jar with these commands.

```shell
bb build jar
bb build install
```

If there was no Git tag pointing to the commit, the jar will have the version: `local`.

## Deploy

Create a Git tag for the version to build (preferrably prefixed with `v`) pointing at the relevant commit.

Run this command (replacing the username and password with your own and the version with the Git tag), which will test, build and deploy the jar to Clojars.

```shell
CLOJARS_USERNAME=username CLOJARS_PASSWORD=CLOJARS_pat ./build/deploy.sh vX.X
```
