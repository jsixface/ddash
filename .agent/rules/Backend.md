---
trigger: always_on
---

## For backend related changes:

* Follow Ktor conventions for routing and response handling

* Ensure proper error handling for API responses

* Use the kermit logging library for logging application events and errors. Add log for Info and debug level.

* Keep code clean and maintainable by following best practices and conventions

* When you have to build, try building for only jvm platform rather than all platforms.

* This project uses amper as a build tool. To run a test use `./amper --platform jvm` so that only JVM is used. This is
  faster.

* You can include only the needed platform by `--include-module` flag


* Update the documentation/README when you make changes to the code and there is a feature change or enhancement.
