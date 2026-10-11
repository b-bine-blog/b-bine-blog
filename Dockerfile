# 메인블로그 운영 이미지 (D-105). GitHub Actions가 만들어 서버로 보낸다.
# 1단계: jar 빌드. 테스트는 Testcontainers(도커)가 필요해 여기서는 돌리지 않는다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test && cp build/libs/*-SNAPSHOT.jar /app.jar

# 2단계: 실행. 업로드 이미지는 /app/uploads(서버 폴더를 붙임, D-75)
FROM eclipse-temurin:21-jre
ENV TZ=Asia/Seoul \
    FILE_STORAGE_ROOT=/app/uploads
WORKDIR /app
COPY --from=build /app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
