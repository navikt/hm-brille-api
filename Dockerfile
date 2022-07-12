FROM navikt/java:17

ENV JAVA_OPTS="-XX:-OmitStackTraceInFastThrow \
               -Xms768m -Xmx1280m"

COPY /build/libs/hm-brille-api-*-all.jar app.jar
