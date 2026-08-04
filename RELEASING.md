# Releasing a New `ktfmt` Version

1. Make sure to have bumped the version in a separated diff, taking care of the `CHANGELOG.md` file (see examples in commit history).
2. Create a new Release in GitHub. A GitHub Action is automatically triggered and builds and publishes the artifacts to
    1. Maven
    2. IntelliJ Plugin marketplace
