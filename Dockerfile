# 운영 이미지 (D-127). GitHub Actions가 만든 jar를 서버로 보내고, 서버의 deploy/deploy.sh가 이 파일로 이미지를 만든다.
# Java 21 실행 환경이 이미지에 함께 들어가므로 서버에 깔린 Java 버전과 상관없이 돈다.
FROM eclipse-temurin:21-jre
ENV TZ=Asia/Seoul \
    FILE_STORAGE_ROOT=/app/uploads
WORKDIR /app
COPY app.jar app.jar
# 비밀 값은 서버의 secrets 파일을 /app/.env로 붙여 읽는다(application.yml spring.config.import)
ENTRYPOINT ["java", "-Xmx512m", "-jar", "/app/app.jar"]
