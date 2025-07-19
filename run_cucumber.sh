#!/bin/bash

WORKSPACE=C:/Documents/ApiFramwork
PORT=8096

java -Dserver.port=$PORT -jar "$WORKSPACE"/target/ApiFramwork-0.0.1-SNAPSHOT.jar > "$WORKSPACE"/common.out & pid4=$!
echo "PID: ${pid4}"

# Wait until app responds (customize URL)
until curl -s http://localhost:$PORT/actuator/health | grep '"status":"UP"'; do
  echo "Waiting for Spring Boot app..."
  sleep 5
done

mvn clean verify

sleep 40s

ls -ltr