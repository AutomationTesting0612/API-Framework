#!/bin/bash

echo "🚀 Starting Employee Management System..."
java -jar /home/Employee-Management-System-0.0.1-SNAPSHOT.jar &

echo "⏳ Waiting 10 seconds before starting AQA Framework..."
sleep 10

echo "🚀 Starting AQA Framework..."
mvn clean install