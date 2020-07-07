FROM cangol/android-gradle
WORKDIR /sekiro-server
COPY . .
RUN ./gradlew sekiro-server:bootJar
EXPOSE 5600 5601 5602
CMD ["java", "-jar", "libs/sekiro-server-0.0.1-SNAPSHOT.jar"]
