
FROM openjdk:17-jdk-slim-buster 
LABEL maintainer="akhil.sharma0612@gmail.com"

COPY ./Employee-Management-System-0.0.1-SNAPSHOT.jar /home/Employee-Management-System-0.0.1-SNAPSHOT.jar

COPY ./target/ApiFramwork-0.0.1-SNAPSHOT.jar /home/ApiFramwork-0.0.1-SNAPSHOT.jar

# Copy the startup script
COPY startup.sh /home/start.sh
RUN chmod +x /home/startup.sh

# Run the script as the container's command
CMD ["/home/start.sh"]