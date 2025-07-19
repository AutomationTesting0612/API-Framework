#!/bin/bash

WORKSPACE=$1
PORT=8096

# Start Spring Boot app
java -Dserver.port=$PORT -jar "$WORKSPACE"/target/ApiFramwork-0.0.1-SNAPSHOT.jar > "$WORKSPACE/common.out" 2>&1 & pid4=$!
echo "Spring Boot PID: ${pid4}"
# Give it some initial time
sleep 10

echo "Waiting for application to be healthy..."
MAX_RETRIES=12
SUCCESS=false

for i in $(seq 1 $MAX_RETRIES); do
  STATUS=$(curl -o /dev/null -s -w "%{http_code}" http://localhost:$PORT/actuator/health)
  if [ "$STATUS" == "200" ]; then
    echo "App is healthy! (HTTP $STATUS)"
    SUCCESS=true
    break
  fi
  echo "Attempt $i: App not ready (HTTP $STATUS), retrying in 5s..."
  sleep 5
done

# Final check and exit if not healthy
if [ "$SUCCESS" != "true" ]; then
  echo "ERROR: App failed to start properly. Check logs at $WORKSPACE/common.out"
  kill $pid4
  exit 1
fi

# Run your tests
mvn clean test

# Optional: Cleanup
sleep 40s
# ✅ Show application logs after test execution
echo "======== Application Logs (post-test) ========"
tail -n 100 "$WORKSPACE/common.out" || echo "No logs found"

ls -ltr
head -n 40 APIReport.html
kill $pid4 || true


