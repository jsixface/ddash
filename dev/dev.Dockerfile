FROM debian:bookworm-slim

# Set the working directory in the container
WORKDIR /app

# Copy the built file into the container
COPY ../build/tasks/_linux-cli_linkLinuxArm64Debug/linux-cli.kexe /app/ddash
COPY ../web/dist /app/web/dist

EXPOSE 8080

LABEL ddash.route="ddash.neon" \
        ddash.enable="true" \
        ddash.category="Utilities" \
        ddash.name="D-Dash"

# Run the application
CMD [ "/app/ddash" ]
