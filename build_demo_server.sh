#!/usr/bin/env bash


now_dir=`pwd`
cd `dirname $0`

shell_dir=`pwd`

mvn  -Dmaven.test.skip=true package appassembler:assemble

if [[ $? != 0 ]] ;then
    echo "build sekiro jar failed"
    exit 2
fi

chmod +x target/sekiro-open-demo/bin/sekiro.sh
sekiro_open_demo_dir=target/sekiro-open-demo

cd ${sekiro_open_demo_dir}

zip -r sekiro-open-demo.zip ./*

mv sekiro-open-demo.zip ../

cd ${now_dir}

echo "the output zip file:  target/sekiro-open-demo.zip"