FROM alpine:3.23

# Set the working directory in the container
WORKDIR /app

# Copy the built file into the container
COPY build/tasks/_linux-cli_linkLinuxX64Release/linux-cli.kexe /bin/ddash

EXPOSE 8080

# Run the application
CMD [ "/bin/ddash" ]
