FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY database ./database
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM tomcat:10.1-jdk17-temurin
RUN rm -rf /usr/local/tomcat/webapps/* && mkdir -p /var/data
COPY --from=build /app/target/ROOT.war /usr/local/tomcat/webapps/ROOT.war
COPY start.sh /start.sh
RUN chmod +x /start.sh
ENV DATABASE_PATH=/var/data/campus_events.db
EXPOSE 8080
CMD ["/start.sh"]
