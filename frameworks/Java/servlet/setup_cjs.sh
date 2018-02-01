#!/bin/bash

fw_depends java resin maven

mvn clean compile war:war -P cjs
rm -rf $RESIN_HOME/webapps/*
cp target/servlet.war $RESIN_HOME/webapps/
resinctl console
