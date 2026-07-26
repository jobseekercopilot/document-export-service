FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY THIRD_PARTY_NOTICES.md .
COPY licenses ./licenses
COPY src ./src

RUN mvn -B --no-transfer-progress clean verify

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY --from=build /app/target/document-export-service-1.0.0.jar app.jar
COPY --from=build /app/licenses/DEJAVU-FONTS-LICENSE.txt /usr/share/licenses/font-dejavu/LICENSE
RUN apk add --no-cache curl font-dejavu
ENV DOCUMENT_EXPORT_PDF_FONT_PATH=/usr/share/fonts/dejavu/DejaVuSans.ttf

EXPOSE 8094
ENTRYPOINT ["java", "-jar", "app.jar"]
