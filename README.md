# data-monitor

Runs a set of data monitoring tasks.

## Installation

Download from http://github.com/shopsmart/data-monitor

## Usage

Run locally:

    $ BD_APP_DATA_MONITOR_ENV_NAME='dev' lein run

Run with prod config:

    $ BD_APP_DATA_MONITOR_ENV_NAME='prod' lein run

Run JAR:

    $ java -jar data-monitor-0.1.0-standalone.jar [args]

Override logging level to debug:

* lein debug

* With log4j2.properties having property set: log4j.rootLogger=${logging.level},file,stdout

    $ java -Dlogging.level=DEBUG -cp xxxxxx.jar  xxxxx.java

* With log4j2.xml (This is an example meant to show that logging configuration can be overriden after the jar is packaged, if the jar is compiled with "lein with-profile debug uberjar", it will use the debug level logging.

    $ java -Dlog4j.configurationFile=log4j2.debug.xml -cp data-monitor-v0.1.0.jar -jar data-monitor-v0.1.0.jar

## Options

## Examples

## Application profiles

Separate from leiningen profiles, which control build details, the application itself implements configuration profiles that determine how the environment is configured, and the values of specific configuration keys configured to conditionally assign values based on application profile name.

Application profile names can be set by overriding the environment variable BD\_APP\_DATA\_MONITOR\_ENV\_NAME, if the variable is not set, it will default to dev.

    $ BD_APP_DATA_MONITOR_ENV_NAME='dev' lein run

## Leiningen profiles

The default lein profile if one is not specified is "dev".

    $ lein pprint :profiles

Profiles can be set with the "with-profile" lein argument.

    $ lein with-profile debug run

For convenience, "lein debug" has been aliased to "lein with-profile debug"

    $ lein debug

## Deployment / Release

To see the steps involved in deployment and / or release "lein pprint :release-tasks";
deployment will stop at the "deploy" task; incrementing version number.

Deploy to S3 FS "repo":

    $ lein deploy --with-profile debug

Release to S3 FS "repo":

    $ lein release --with-profile prod :patch

Release with updated version number (minor version):

    $ lein release -with-profile default :patch

The following keywords may be used to set the tag / release version numbers explictly: "lein release [$LEVEL] task", where $LEVEL can be refer to any of :major, :minor, :patch, :alpha, :beta, or :rc.

If not specified: "lein release" will increment the version as if "lein release :patch" were used.

Arbitrary / specific artifacts may be deployed from disk:

    $ lein deploy data-monitor com.blueant/fancypants 1.0.1 fancypants.jar pom.xml

## Project file repository configuration

S3 release target (URL: https://github.com/technomancy/s3-wagon-private)

Authentication configuration:
  - "lein help deploying"
  - "lein help deploy"
  - Keywords :username and :passphrase are synonymous with AWS Access Key and Secret Key (respectively) using S3 nomenclature
  - Local unencrypted lein config
    - NOTE: The echo commands are pseudo code; manually editing the target files is recommended to ensure a proper merge with existing configuration
    - NOTE: For other deployment modules and release tasks (such as SSH/SCP), :password is used in place of :passhrase 
    - For use with GPG encryption
      - $ echo '{#"s3p://mybucket/" {:username "AKIA2489AE28488" :passphrase "98b0b104ca1211e19a6c"}}' >> ~/.lein/credentials.clj
      - follow GPG steps below
      - $ rm ~/.lein/credentials.clj
    - For use without GPG encryption (safe if S3 bucket is full disk encrypted)
      - $ echo '{:auth {:repository-auth {#"s3p://mybucket/" {:username "test-username" :passphrase "test-password"}}}} >> (~/.lein/profiles.clj) 
      - ":creds :gpg" in project.clj :repositories and / or :deploy-repositories config
        - NOTE: The hash string modifier denotes a regex match against project.clj configured :repositories and / or :deploy-repositories.
                For exact matching, a regular string may be used.
        - NOTE: Auth profile map uses keyword ":password" vs. ":passphrase"
  - GPG encrypted file (~/.lein/credentials.clj.gpg)
    - $ gpg --default-recipient-self -e ~/.lein/credentials.clj > ~/.lein/credentials.clj.gpg
    - ":creds :gpg" in project.clj :repositories and / or :deploy-repositories config
  - env vars 
    - Default env vars
        - LEIN_USERNAME, LEIN_PASSPHRASE
        - ":creds :env" in project.clj :repositories and / or :deploy-repositories config
    - Custom env vars
        - AWS_ACCESS_KEY, AWS_SECRET_KEY
        - ":username :env/aws_access_key :passphrase :env/aws_secret_key" in project.clj :repositories and / or :deploy-repositories config
  
## Future Features and Refactoring Agenda

* WLM configuration (ensure monitoring queries run in separate queue from ETL / work queries running the queries which monitor will checking); should not get stuck in a queue and miss alerting; could possible use superuser queue but that would have other implicaitons
* DDL changes for dim\_network (active networks)
* H2 integration;
    * JDBC driver
    * Wrapper
    * In memory db ?
        * DDL to setup and tear down
            * meta data about 
    * Data model for app
        * task meta tables with things like number of days per network for FC and FCD checks, alert frequency, etc.
    * Sync data and schema to RS and back
* Scheduling
    * Run $task or $task-set on $cron-schedule
* S3 / AWS clj SDK integration
* Deploy scripts / plugins / project.clj modifications
* Additional alerts / checks
    * Modify SQL alerts to denote that current revenue loads are running (e.g. FC missing revenue); implement with pre / post triggers ?
* Additional task types and exec methods
    * SSH / SCP
    * SQL
        * JDBC data task exec method support (vs. psql)
        * SQL file includes, meta tags that allow SQL to be run, values to be checked and assertions run against returned values, etc. 
    * shell script / local command
    * pagerduty
        * create additional PD side intergration points (email intergration and / or API alert IDs -- not sure if pre-made intergration configuration is required when using API vs. email)
    * Email
        * beetl log alert, send large text alerts as attachments; change name of alerts to the descriptive name and make command and other attributes / meta part of the message body
* Error check lib
    * Support variance of values by percent, min, max, etc.
    * Support comparing current value to previously retrieved value
    * Fail if $condition-or-predicate is met / not met $n times or $n times in $t time period
    * Aggregate failures, fail on first error, digest
* Pager duty integration
* Asgard integration ?
* Action library
    * email
        * query results
        * text
        * html / text
        * include graph / chart
* log4j setup
* Create lein new app profile with:
    * common dependencies
    * pre-configured project.clj
    * (one version for lib and one for CLI)
    * config files with includes to secret and env configs (symlinks to local file)
    * .gitignore
* Deploy / Release
    - Test building with dirs with hyphens (-) renamed to underscores (\_) or (:ns declarations using one or the other or none) for "lein uberjar" class not found "clojure.lang.Var' error; ALSO test project.clj :aot and ^aot enable / disable, :all vs [data-monitor.core]
    * mkdir log dir on release to server
    * Integrate with ansible with lein deployment (or vice-versa)
    * Add git project dependencies with sym links 
    * Secret config management
        * Maybe script to diff local version to S3; merge changes from prod version of config and resolve conflicts and lint check, need common secret.edn shared across projects; CLI util(s) to help administer conf files (create new file, merge files, 
    * Build locally; release to shared repo (S3, or open source repository); ansible tasks to scp file to target server, ?merge secret config?
    * Add release task to run unit / smoke tests firsts
    * Setup env vars for environment
    * ansible tasks to stop / start server
    * init.d scripts, support service $app-name start / stop / refresh; ansible tasks to set these up
    * Versioning
        * Figure out how to manage -SNAPSHOT in version number; see lein release plugin that auto removes -SNAPSHOT ? lein deploy / release default to snapshot repo if version contains -SNAPSHOT; can we remove it by supplying args to "lein deploy [version]" ?
    * Try SSH lein plugin to scp file to locally mounted s3fs (brew install homebrew/fuse/s3fs)
* Add shell script to "lein run or run the jar"
* Add CLI argument processing
    * Allow one time run of all or specific tests
    * override failure actions (stdout, email, log-file, etc.)
* Add com.bradsdeals to package / namespace names
* Move configuration out of JAR file
    * log4j default in jar, look for config on disk; use monitorInterval if using disk version so log level can be changed dynamically
    * move config files onto local disk as part of deploy so they may be updated w/out requiring a release
        * config lib could optionally copy from S3 caching to memfs or local disk on startup
        * program needs to monitor file for changes, or setup signal handler to catch a "reset" signal that would initiate config reload (might need to differentiate between properties that can be dynamically changed and those that require a restart)
* Logging config
    * allow "lein run :with-profile dev (default if not profile set)" to use the INFO version; but prod version to use TRACE version of log4j2.xml; profile debug uses .debug.xml (maybe just put the TRACE version of the file in the uberjar profile (need to test this)?)
* Restructure file layout
    * Remove unneeded files / dirs
    * Move log4j2.profile-name.jar into resources/config/log4j2/profiles/
    * Create subdirs in resources/sql (by type (assertion or variance) or target / source tables or category (e.g. revenue checks)
* Testing
    * Unit testing
    * Integration testing
    * Lint checking
    * Smoke test
    * Travis CI
        * travis.yaml
        * google travis deploy artifact s3
    * Setup profiling and update README.md with instructions for attaching debugger / tracer / IDE (Eclipse) to JVM
    * DBUnit tests
* Setup / configure clj 1.9 spec(s) (a.k.a. schema) (http://clojure.org/guides/spec)
* Zookeeper locking
    * Store application state
    * PID locking
    * Immplemnt lock graph that work with multi-tenant system
        * e.g. data-monitor and beetl depend on RS being up, should not start or run during RS maintenance window
    * Store retartability information
* Implement database migrations (pull out beetl migrations into separate lib / repo and add wrappers / tools for building local / dev / staging, etc.

### Bugs


