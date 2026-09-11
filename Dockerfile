FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY THIRD_PARTY_NOTICES.md .
COPY licenses ./licenses
COPY src ./src

RUN mvn -B --no-transfer-progress clean verify

FROM eclipse-temurin:17-jre-alpine

# Upgrade the OpenSSL runtime packages to the CVE-2026-14456 fixed build.
RUN apk add --no-cache --upgrade \
    libcrypto3=3.5.8-r0 \
    libssl3=3.5.8-r0 \
    expat=2.8.4-r0 \
    openssl=3.5.8-r0

WORKDIR /app
COPY --from=build /app/target/document-export-service-1.0.0.jar app.jar
COPY --from=build /app/licenses/DEJAVU-FONTS-LICENSE.txt /usr/share/licenses/font-dejavu/LICENSE
RUN apk add --no-cache curl font-dejavu
ENV DOCUMENT_EXPORT_PDF_FONT_PATH=/usr/share/fonts/dejavu/DejaVuSans.ttf

EXPOSE 8094
ENTRYPOINT ["java", "-jar", "app.jar"]
