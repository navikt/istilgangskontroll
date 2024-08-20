FROM gcr.io/distroless/java21
WORKDIR /app
COPY build/libs/app.jar app.jar
ENV JDK_JAVA_OPTIONS="-Dlogback.configurationFile=logback.xml"
ENV TZ="Europe/Oslo"
EXPOSE 8080
USER nonroot
CMD [ "app.jar" ]
