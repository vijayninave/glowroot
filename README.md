Glowroot &nbsp;&nbsp; [![Build Status](https://travis-ci.org/glowroot/glowroot.png?branch=master)](https://travis-ci.org/glowroot/glowroot)
=========

## Quick start

1. Download and unzip the [latest snapshot](https://glowroot.s3.amazonaws.com/snapshots/latest/glowroot-dist.zip)
2. Add `-javaagent:path/to/glowroot.jar` to your application's JVM arguments
3. Start your application
4. Connect to http://localhost:4000

## Questions, bugs, feature requests

Post 'em all to the [GitHub issue tracker](https://github.com/glowroot/glowroot/issues).

## Contributing

#### How to build it

Glowroot uses [Bower](http://bower.io) and [Grunt](http://gruntjs.com) to build its web assets (dependency management, Javascript concat/minify, LESS compile/minify, AngularJS template concat/minify, asset revving and more).

To install Bower and Grunt, first install [Node.js](http://nodejs.org), then install Bower and Grunt from the command line:

    npm install -g bower grunt-cli

From now on, building is easy:

    mvn clean install

Binary and source distributions are built under distribution/target.

#### How to hack on it

Run org.glowroot.sandbox.ui.UiSandboxMain under a debugger inside your favorite IDE. It starts Glowroot and generates a variety of sample traces to give the UI something to display and to help with manual testing. Connect your browser to http://localhost:4000.

If you are working on the UI, you either need to run 'grunt' to re-build the web assets after each change, or (better) run:

    grunt serve

and connect your browser to http://localhost:9000.  **Note:** 'grunt' and 'grunt serve' should be run from the [core](core) subdirectory.

'grunt serve' serves up the Glowroot web assets to the browser without the concat/minify/rev step, which makes testing/debugging much easier. It reverse proxies non- static resource requests to http://localhost:4000 to be handled by Glowroot. It also watches for changes to the files and performs live-reload of the assets inside the browser.

#### How to test it

All automated tests are run during the maven build:

    mvn test

They can also be found in the following locations and run as standard JUnit tests inside your favorite IDE:

* Unit tests are under [core](core)
* Integration tests are under [testing/integration-tests](testing/integration-tests)
* WebDriver tests are under [testing/webdriver-tests](testing/webdriver-tests)
* Servlet plugin tests are under [plugins/servlet-plugin](plugins/servlet-plugin)
* Jdbc plugin tests are under [plugins/jdbc-plugin](plugins/jdbc-plugin)
* Logger plugin tests are under [plugins/logger-plugin-tests](plugins/logger-plugin-tests)

Thanks to [Sauce Labs](https://saucelabs.com), the WebDriver tests run against Firefox (latest), Chrome (latest), Safari (5, 6, 7) and IE (9, 10, 11) as part of every Travis CI build (see the jobs with TARGET=saucelabs).

## Microbenchmarks

Microbenchmarks to analyze the monitoring overhead are written using the excellent [JMH](http://openjdk.java.net/projects/code-tools/jmh/) benchmark harness. They can be built and run using:

    mvn clean package
    java -jar target/microbenchmarks.jar -jvmArgs -javaagent:target/microbenchmarks.jar

from the following locations:

* [testing/microbenchmarks](testing/microbenchmarks)
* [plugins/servlet-plugin-microbenchmarks](plugins/servlet-plugin-microbenchmarks)
* [plugins/jdbc-plugin-microbenchmarks](plugins/jdbc-plugin-microbenchmarks)

## Code quality

[SonarQube](http://www.sonarqube.org) is used to check Java coding conventions, code coverage, duplicate code, package cycles and much more. It is run as part of every Travis CI build (see the job with TARGET=sonar) and the analysis is reported to [http://sonar.glowroot.org](http://sonar.glowroot.org).

[Checker Framework](http://types.cs.washington.edu/checker-framework/) is used to eliminate fear of `null` with its rigorous [Nullness Checker](http://types.cs.washington.edu/checker-framework/current/checker-framework-manual.html#nullness-checker). It is run as part of every Travis CI build (see the job with TARGET=checker) and any violation fails the build.

## License

Glowroot source code is licensed under the Apache License, Version 2.0.

See [Third Party Software](https://github.com/glowroot/glowroot/wiki/Third-Party-Software) for license detail of third party software included in the binary distribution.
