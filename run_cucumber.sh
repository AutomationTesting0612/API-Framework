#!/bin/bash

WORKSPACE=$1

java -jar "$WORKSPACE"/target/ApiFramwork-0.0.1-SNAPSHOT.jar

mvn clean verify

sleep 40s

ls -ltr