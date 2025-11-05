#!/bin/bash

# Set Java 21 for the build
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Run the Gradle build
./gradlew "$@"