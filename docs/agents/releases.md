# Releases

A release reaches the Pack (5thlayer/factoryworks) through the local maven repository (`~/.m2`) at a version of its own. The Pack pins that version and compares the jar's sha256. That only works if the jar published at a version is the one jar that version ever names.

Craftworks is a car in the `release-train` skill: it requires Groundworks (not bundled, never jar-in-jar, from `~/.m2` only) and not Beltworks, and the Pack pins its jar directly. CI has no `~/.m2` to take Groundworks from yet. A released version is then uploaded to Modrinth and CurseForge from the maintainer's machine (below). CI builds and tests on every push but never publishes.

## The changelog

Each change a player or pack author notices adds its line under `## Unreleased` in `CHANGELOG.md` when it lands, written in the glossary's terms rather than as the commit subject, with its issue number. A release ships what Unreleased lists, so the changelog is written as the work is done and never reconstructed from commits.

## Cutting a release

`scripts/release.sh <version>` from a clean main. libworks' ADR 0001 sets the semver, and ADR-0008 names what it covers here: below 1.0 only a change that breaks a pack author's data, config, calls or players' saves bumps the minor version, and an addition or a fix is the next patch. The script refuses a version that is already tagged or in `~/.m2`, and an empty Unreleased. It then:

1. sets `mod_version` and turns `## Unreleased` into `## <version>` under a fresh, empty Unreleased
2. runs the build and the game tests, putting both files back if either fails
3. commits `chore: release <version>`, runs `publishToMavenLocal`, and tags `v<version>` with the jar's sha256
4. uploads the jar to Modrinth and CurseForge with `scripts/upload.py` (below). A failed upload leaves the local release and the tag in place; the script names the site that failed, and `scripts/upload.py --site <site> <version>` retries it. `--no-upload` stops before this step and prints the upload command, for the release train, which uploads only once the user says to push. Under `MAVEN_REPO_LOCAL`, a trial run, the upload is only a dry run.

It pushes nothing to git, and ends by printing the push command. The Pack then moves its pin with `scripts/sync-local-jars.py craftworks=<version>`. To try the script out, set `MAVEN_REPO_LOCAL` to a scratch folder and run it in a throwaway clone: a version published to the real `~/.m2` is permanent.

## Uploading to Modrinth and CurseForge

`scripts/upload.py <version>` uploads a version already in `~/.m2` to both sites: the jar there, byte for byte, with that version's changelog section as its notes, for Minecraft `minecraft_version` on NeoForge, as the release type `upload_release_type` in `gradle.properties` names (`release`, `beta` or `alpha`) or, left empty, as beta below 1.0 and release from it, with EMI as a required dependency. Each site's token comes from the environment and is never printed. The tokens live in 1Password, and `publish/upload.env` names them there; when a token is missing the upload runs itself again through `op run --env-file=publish/upload.env`, which fills them in for that run only. So `scripts/release.sh <version>` and `scripts/upload.py <version>` need nothing exported; 1Password asks to be unlocked. An unknown release type is refused before either site is contacted, and `--dry-run` shows the type it would send.

- Modrinth: `MODRINTH_TOKEN`, the 1Password item "Beltworks Modrinth", shared with Beltworks since a token belongs to the account: a personal access token with the scopes Create versions, Read versions and Read projects.
- CurseForge: `CURSEFORGE_TOKEN`, the 1Password item "Beltworks CurseForge": an upload API token. The upload API can't list a project's files, so the check for a version CurseForge already has reads the website's own listing (`www.curseforge.com/api/v1/mods/<id>/files`), which needs no key but is undocumented: if it changes, that check fails and the upload stops. It doesn't show a file still under CurseForge's review, so a version is never uploaded again while one waits. 0.1.2 was uploaded by hand when the projects were created.

The projects default to Craftworks' own, Modrinth `v6CdwRwB` and CurseForge `1715876`, and `MODRINTH_PROJECT_ID` and `CURSEFORGE_PROJECT_ID` override them.

It refuses, before contacting either site, a version missing from `~/.m2` or from the changelog, and a jar lacking the licensing `checkJarLicensing` requires (`LICENSE` and `LICENSES/MIT.txt`, which the build puts in every jar from 0.1.3 on). Each site then goes on its own: a site that already has the version, or whose upload fails, is refused without touching the other, and `--site modrinth` or `--site curseforge` retries just that one. `--dry-run` prints the requests and contacts nothing. `MODRINTH_API_URL`, `CURSEFORGE_UPLOAD_URL` and `CURSEFORGE_API_URL` (the listing's site) point it elsewhere, and its tests (`python3 -m unittest discover scripts/tests`) run it against a stand-in server on localhost.

## A published version is final

A version in `~/.m2` never changes. A fix is the next patch version. `publishToMavenLocal` refuses a version that is already there (`build.gradle`), and nothing is deleted or overwritten by hand to get past it. The same holds on Modrinth and CurseForge: `scripts/upload.py` refuses a version a site already has, and nothing there is deleted or replaced.

## Tags

A release is tagged `v<version>`, annotated with its jar's sha256. `git tag -l 'v*' -n9` lists them.
