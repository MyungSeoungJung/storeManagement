#FROM 기반으로 할 이미지
FROM eclipse-temurin:17-jre

#VOLUME /tmp

ARG JAR_FILE=build/libs/*.jar

COPY ${JAR_FILE} app.jar

ENTRYPOINT ["java","-jar", "/app.jar"]

