# gRPC-Web JMeter Plugin - Release Notes

## Version 1.0.2 - July 4, 2025

### ✨ New Features

* **Support for Relative Proto Paths**

  * Added a checkbox UI (`Use relative path`) to toggle storing proto file paths as relative to the working directory (`user.dir`).
  * This improves portability of `.jmx` test plans across different machines.

### ✅ Enhancements

* Automatically converts proto file path to **absolute** when running `protoc`, avoiding common path resolution errors.
* Improved error handling and logging when parsing `.proto` files.
* Better support for multi-service `.proto` files — combo boxes are dynamically restored when reloading a saved test plan.

### 🐞 Bug Fixes

* Fixed a bug where reloading a test plan did not restore the selected "Use relative path" checkbox state.
* Fixed edge case where `protoc` would fail on Windows due to inconsistent path separators.

### 🔧 Developer Notes

* `GrpcWebSamplerGui` now stores the `useRelativePath` setting via `GrpcWebSampler.setUseRelativePath()` and restores it with `getUseRelativePath()`.
* All `protoc` invocations now use `absolute` paths internally, even when relative path is enabled in GUI.
* Logging improved with more granular `System.out` and `System.err` for debugging failures.

---

Thank you for using the gRPC-Web JMeter Plugin! If you encounter issues or want to contribute, visit [GitHub Repo](https://github.com/badrusalam11/grpcweb-jmeter-plugin).
