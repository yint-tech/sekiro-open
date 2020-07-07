FROM registry.cn-hangzhou.aliyuncs.com/kennylee/gradle-node:v5.4.1-v12 as build
#FROM cangol/android-gradle as build
WORKDIR /app
COPY . .
RUN ./gradlew sekiro-server:bootJar

FROM openjdk:8-alpine
WORKDIR /app
COPY --from=build /app/sekiro-server/build/libs/sekiro-server-0.0.1-SNAPSHOT.jar .
EXPOSE 5600 5601 5602 5603
CMD ["java", "-jar", "/app/sekiro-server-0.0.1-SNAPSHOT.jar"]

