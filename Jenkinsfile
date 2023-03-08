@Library(value='jenkins-pipeline-library@master', changelog=false) _
pipelineRDPCSongSearch(
    buildImage: "adoptopenjdk/openjdk11:jdk-11.0.7_10-alpine-slim",
    dockerRegistry: "ghcr.io",
    dockerRepo: "icgc-argo/song-search",
    gitRepo: "icgc-argo/song-search",
    testCommand: "./mvnw test -ntp",
    helmRelease: "song-search"
)
