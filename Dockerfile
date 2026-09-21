FROM node:24-alpine AS web-build
WORKDIR /workspace
COPY package.json package-lock.json ./
COPY apps/web/package.json apps/web/package.json
RUN npm ci
COPY apps/web apps/web
RUN npm run build:web

FROM eclipse-temurin:25-jdk-alpine AS api-build
WORKDIR /workspace/apps/api
COPY apps/api/gradlew ./
COPY apps/api/gradle gradle
COPY apps/api/settings.gradle apps/api/build.gradle ./
RUN chmod +x gradlew
COPY apps/api/src src
COPY --from=web-build /workspace/apps/web/dist src/main/resources/static
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=api-build /workspace/apps/api/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
