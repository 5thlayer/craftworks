# Releases

A release reaches the Pack (adamico/planetary-factory) through the local maven repository (`~/.m2`) at a version of its own. The Pack pins that version and compares the jar's sha256. That only works if the jar published at a version is the one jar that version ever names.

Craftworks is a car in the `release-train` skill: it depends on neither Groundworks nor Beltworks, and the Pack pins its jar directly.

## The changelog

Each change a player or pack author notices adds its line under `## Unreleased` in `CHANGELOG.md` when it lands, written in the glossary's terms rather than as the commit subject, with its issue number. A release ships what Unreleased lists, so the changelog is written as the work is done and never reconstructed from commits.

## Cutting a release

`scripts/release.sh <version>` from a clean main. Below 1.0, a breaking change for a pack author (a recipe field, a config key, a hook's signature) bumps the minor version, and an addition or a fix is the next patch. The script refuses a version that is already tagged or in `~/.m2`, and an empty Unreleased. It then:

1. sets `mod_version` and turns `## Unreleased` into `## <version>` under a fresh, empty Unreleased
2. runs the build and the game tests, putting both files back if either fails
3. commits `chore: release <version>`, runs `publishToMavenLocal`, and tags `v<version>` with the jar's sha256

It pushes nothing, and ends by printing the push command. The Pack then moves its pin with `scripts/sync-local-jars.py craftworks=<version>`. To try the script out, set `MAVEN_REPO_LOCAL` to a scratch folder and run it in a throwaway clone: a version published to the real `~/.m2` is permanent.

## A published version is final

A version in `~/.m2` never changes. A fix is the next patch version. `publishToMavenLocal` refuses a version that is already there (`build.gradle`), and nothing is deleted or overwritten by hand to get past it.

## Tags

A release is tagged `v<version>`, annotated with its jar's sha256. `git tag -l 'v*' -n9` lists them.
