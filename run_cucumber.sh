#!/bin/bash

WORKSPACE=$1
PORT=8096

# Start Spring Boot app
java -Dserver.port=$PORT -jar "$WORKSPACE"/target/ApiFramwork-0.0.1-SNAPSHOT.jar > "$WORKSPACE"/common.out & pid4=$!
echo "Spring Boot PID: ${pid4}"

# Wait for app to start (max 60s wait)
echo "Waiting for application to be healthy..."
for i in {1..12}; do
  STATUS=$(curl -o /dev/null -s -w "%{http_code}" http://localhost:$PORT/actuator/health)
  if [ "$STATUS" == "200" ]; then
    echo "App is healthy! (HTTP $STATUS)"
    break
  fi
  echo "Attempt $i: App not ready (HTTP $STATUS), retrying in 5s..."
  sleep 5
done

# Final check
STATUS=$(curl -o /dev/null -s -w "%{http_code}" http://localhost:$PORT/actuator/health)
if [ "$STATUS" != "200" ]; then
  echo "ERROR: App failed to start properly. Final status: $STATUS"
  kill $pid4
  exit 1
fi

# Run tests
mvn clean test

# Optional: Wait or cleanup
sleep 10

# Kill app after tests
kill $pid4 || true

