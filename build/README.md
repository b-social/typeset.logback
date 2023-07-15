# Building/deploying JARs

Run tests:

```shell
clj -T:build compile
clj -X:test
```

Create a Git tag for the version to build (make sure the tag is prefixed with
`v`) and make sure you have no uncommitted local changes.

Build the jar with this command.

```
clojure -T:build jar
```

Deploy with this command (replacing the username and password with your own).

```sh
CLOJARS_USERNAME=username CLOJARS_PASSWORD=CLOJARS_pat clojure -T:build deploy
```
