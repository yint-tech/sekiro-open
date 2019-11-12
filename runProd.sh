#!/usr/bin/env bash
now_dir=`pwd`
cd `dirname $0`

history_pid=`ps -ef | grep sekiro-server | grep -v "grep" | awk '{print $2}'`

echo history_pid:${history_pid}
if [ -n "${history_pid}" ] ;then
    echo kill pid ${history_pid}
    kill -9 ${history_pid}
fi

echo refresh code
git clean -dfx
git reset --hard
git pull

echo "assemble jar"
./gradlew sekiro-server:bootJar

echo "run project"

nohup java -jar sekiro-server/build/libs/sekiro-server-0.0.1-SNAPSHOT.jar >/dev/null 2>&1  &
#java -jar sekiro-server/build/libs/sekiro-server-0.0.1-SNAPSHOT.jar


echo "script succes..."
cd ${now_dir}
