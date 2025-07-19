#!/bin/bash

WORKSPACE=C:/Documents/ApiFramwork
PORT=8096

java -Dserver.port=$PORT -jar "$WORKSPACE"/target/ApiFramwork-0.0.1-SNAPSHOT.jar > "$WORKSPACE"/common.out & pid4=$!
echo "PID: ${pid4}"

# Wait until app responds (customize URL)
STATUS=$(curl -o /dev/null -s -w "%{http_code}\n" http://localhost:$PORT/actuator/health)

echo "STATUS :$STATUS, Exit code: $?"

mvn clean verify

sleep 40s

ls -ltr