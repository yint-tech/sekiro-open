#!/usr/bin/env bash


now_dir=`pwd`
cd `dirname $0`

shell_dir=`pwd`


mvn  clean -Dmaven.test.skip=true install

if [[ $? != 0 ]] ;then
    echo "build sekiro jar failed"
    exit $?
fi

cd sekiro-service-demo
sekiro_service_base=`pwd`

mvn  -Dmaven.test.skip=true package appassembler:assemble

chmod +x target/sekiro-release-demo/bin/sekiro.sh

sekiro_release_demo_dir=${sekiro_service_base}/target/sekiro-release-demo

cd ${sekiro_release_demo_dir}

zip -r sekiro-release-demo.zip ./*

mv sekiro-release-demo.zip ../

cd ${now_dir}

echo "the output zip file:  ${sekiro_service_base}/target/sekiro-release-demo.zip"