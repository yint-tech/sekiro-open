#!/usr/bin/env bash

cd `dirname $0`

version_text=`cat sekiro-lib/build.gradle | grep version | grep -v options`
echo ${version_text}
if [[ ${version_text} =~ "SNAPSHOT" ]] ;then
    ./gradlew sekiro-lib:publishMavenJavaPublicationToSonatypeSnapshotRepository --no-daemon --no-parallel
else
    ./gradlew sekiro-lib:publishMavenJavaPublicationToSonatypeRepository --no-daemon --no-parallel
fi
