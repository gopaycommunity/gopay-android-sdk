module.exports = {
    branches: [
        "master"
    ],
    plugins: [
        "@semantic-release/commit-analyzer",
        [
            "@semantic-release/npm",
            {
                npmPublish: false
            }
        ],
        [
            "@semantic-release/release-notes-generator",
            {
                // Plain version headers and commit hashes: no links to the repository host in the
                // release notes or CHANGELOG.md.
                writerOpts: {
                    finalizeContext: (context) => ({ ...context, linkReferences: false, linkCompare: false })
                }
            }
        ],
        "@semantic-release/changelog",
        [
            "@semantic-release/exec",
            {
                prepareCmd: "./scripts/set-version.sh ${nextRelease.version}"
            }
        ],
        [
            "@semantic-release/git",
            {
                assets: [
                    "CHANGELOG.md",
                    "package.json",
                    "README.md",
                    "sdk/gradle.properties"
                ]
            }
        ]
    ],
    tagFormat: "${version}"
};
