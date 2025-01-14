#!/bin/sh

# Build the jar from a Git tag and deploy it!
#
#   CLOJARS_USERNAME=... CLOJARS_PASSWORD=... ./build/deploy.clj v0.1

version="$1"

if [ -z "$version" ]; then
    >&2 echo "Must provide a version: $0 vX.X"
    exit 1
else
    echo "Version: $version"
fi

set -x

git worktree add --detach "deploy-$version" "$version"
pushd "deploy-$version"

(
  set -e
  bb build compile
  bb test
  bb build jar
  bb build deploy
)

popd
git worktree remove "deploy-$version"
