FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon
RUN cp build/libs/*.jar application.jar \
	&& java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
# 운영 전용 이미지라, compose에서 프로필을 빠뜨려도 관리자·JWT 키 검사가 꺼진 기본 설정으로 뜨지 않게 한다.
ENV SPRING_PROFILES_ACTIVE=prod
USER ubuntu
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
