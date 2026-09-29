FROM debian:bookworm-slim

# CA certificates are needed for HTTPS calls (e.g. the OIDC identity provider)
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Set the working directory in the container
WORKDIR /app

# Copy the built file into the container
COPY bin/linux-cli.kexe /app/ddash
COPY web/dist /app/web/dist

RUN chmod +x /app/ddash
EXPOSE 8080

LABEL ddash.enable="true" \
        ddash.category="Utilities" \
        ddash.name="D-Dash"

# Run the application
CMD [ "/app/ddash" ]
