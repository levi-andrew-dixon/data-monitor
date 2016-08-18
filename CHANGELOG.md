# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]
### Changed

- Pagerduty integration

## [0.1.1] - 2016-08-14
### Changed
- Setup custom aero / EDN tag handlers and file resolvers to support loading config files both when run outside of a jar as files (lein run) and from a jar (java -jar data-monitor-v0.1.0.jar) as resources.  This also allows resolution of relative paths by #include tags in EDN files.
- Setup deployment and releasing to S3 bucket; this paves the way towards having a simplified release process using "lein release", "lein deploy" to deploy to S3 repo (which can be used for dependency resolution as well) and Ansible for the deployment of artifacts to server

### Removed
- Unecessary files created from "lein app" skeleton

### Fixed

## 0.1.0 - 2016-07-29
### Added
- Errors loading configuration and processing #include tags from JAR files

[Unreleased]: https://github.com/shop-smart/data-monitor/compare/0.1.1...HEAD
[0.1.1]: https://github.com/shopsmart/data-monitor/compare/0.1.0...0.1.1
